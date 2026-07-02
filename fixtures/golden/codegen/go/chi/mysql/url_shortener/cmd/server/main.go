package main

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	sdkresource "go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/trace"

	"github.com/generated/url-shortener/internal/admin"
	"github.com/generated/url-shortener/internal/auth"
	"github.com/generated/url-shortener/internal/config"
	"github.com/generated/url-shortener/internal/database"
	"github.com/generated/url-shortener/internal/extensions"
	"github.com/generated/url-shortener/internal/handlers"
	"github.com/generated/url-shortener/internal/services"
)

var (
	httpRequests = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "http_requests_total",
		Help: "HTTP requests served, by method, route pattern, and status code.",
	}, []string{"method", "path", "status"})
	httpRequestDuration = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Name: "http_request_duration_seconds",
		Help: "HTTP request duration in seconds, by method and route pattern.",
	}, []string{"method", "path"})
)

func trackRequests(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		ww := middleware.NewWrapResponseWriter(w, r.ProtoMajor)
		next.ServeHTTP(ww, r)
		// The chi route pattern, not the raw URL: raw paths embed ids and would
		// blow up label cardinality.
		path := chi.RouteContext(r.Context()).RoutePattern()
		if path == "" {
			path = "unmatched"
		}
		httpRequests.WithLabelValues(r.Method, path, strconv.Itoa(ww.Status())).Inc()
		httpRequestDuration.WithLabelValues(r.Method, path).Observe(time.Since(start).Seconds())
	})
}

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	slog.SetDefault(logger)

	cfg, err := config.Load()
	if err != nil {
		slog.Error("failed to load config", "error", err)
		os.Exit(1)
	}

	db, err := database.Connect(context.Background(), cfg.DatabaseURL)
	if err != nil {
		slog.Error("failed to connect to database", "error", err)
		os.Exit(1)
	}
	defer func() { _ = db.Close() }()

	urlMappingSvc := services.NewUrlMappingService(db, cfg)
	urlMappingHandler := handlers.NewUrlMappingHandler(urlMappingSvc)
	r := chi.NewRouter()
	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(middleware.Logger)
	// Outside Recoverer, so a panicking request is still counted (as the 500
	// Recoverer writes).
	r.Use(trackRequests)
	// Renames the server span to "METHOD /route/{pattern}" once routing has
	// resolved; with tracing off the context carries a no-op span and this
	// costs nothing.
	r.Use(func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
			next.ServeHTTP(w, req)
			if pattern := chi.RouteContext(req.Context()).RoutePattern(); pattern != "" {
				trace.SpanFromContext(req.Context()).SetName(req.Method + " " + pattern)
			}
		})
	})
	r.Use(middleware.Recoverer)
	r.Use(middleware.Timeout(30 * time.Second))

	// chi panics on r.Use(...) after routes are registered, so the user hook
	// runs here — before any generated route wiring — and may install both
	// middleware and additional routes.
	extensions.Register(r, db)

	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"status":"ok"}`))
	})

	r.Get("/ready", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		if err := db.PingContext(r.Context()); err != nil {
			slog.Error("readiness check failed", "error", err)
			w.WriteHeader(http.StatusServiceUnavailable)
			_, _ = w.Write([]byte(`{"status":"unavailable"}`))
			return
		}
		_, _ = w.Write([]byte(`{"status":"ready"}`))
	})

	r.Method(http.MethodGet, "/metrics", promhttp.Handler())

	admin.Register(r, db, auth.RequireAdmin(cfg.AdminToken))

	r.Post("/shorten", urlMappingHandler.Shorten)
	r.Get("/urls", urlMappingHandler.ListAll)
	r.Get("/{code}", urlMappingHandler.Resolve)
	r.Delete("/{code}", urlMappingHandler.Delete)
	// Tracing is opt-in: without OTEL_EXPORTER_OTLP_ENDPOINT nothing is wired
	// and no exporter ever dials out.
	handler := http.Handler(r)
	if os.Getenv("OTEL_EXPORTER_OTLP_ENDPOINT") != "" {
		exporter, err := otlptracehttp.New(context.Background())
		if err != nil {
			slog.Error("failed to initialise OTLP trace exporter", "error", err)
			os.Exit(1)
		}
		serviceName := os.Getenv("OTEL_SERVICE_NAME")
		if serviceName == "" {
			serviceName = "url_shortener"
		}
		res, err := sdkresource.Merge(
			sdkresource.Default(),
			sdkresource.NewSchemaless(attribute.String("service.name", serviceName)),
		)
		if err != nil {
			slog.Error("failed to build trace resource", "error", err)
			os.Exit(1)
		}
		tp := sdktrace.NewTracerProvider(
			sdktrace.WithBatcher(exporter),
			sdktrace.WithResource(res),
		)
		otel.SetTracerProvider(tp)
		defer func() {
			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()
			_ = tp.Shutdown(ctx)
		}()
		handler = otelhttp.NewHandler(r, "http.server")
	}

	server := &http.Server{
		Addr:         fmt.Sprintf(":%d", cfg.Port),
		Handler:      handler,
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	done := make(chan os.Signal, 1)
	signal.Notify(done, os.Interrupt, syscall.SIGTERM)

	go func() {
		slog.Info("starting server", "port", cfg.Port)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			slog.Error("server error", "error", err)
			os.Exit(1)
		}
	}()

	<-done
	slog.Info("shutting down")
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = server.Shutdown(ctx)
}
