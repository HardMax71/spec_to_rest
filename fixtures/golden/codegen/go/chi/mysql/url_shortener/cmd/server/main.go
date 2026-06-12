package main

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"

	"github.com/generated/url-shortener/internal/admin"
	"github.com/generated/url-shortener/internal/auth"
	"github.com/generated/url-shortener/internal/config"
	"github.com/generated/url-shortener/internal/database"
	"github.com/generated/url-shortener/internal/extensions"
	"github.com/generated/url-shortener/internal/handlers"
	"github.com/generated/url-shortener/internal/services"
)

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

	admin.Register(r, db, auth.RequireAdmin(cfg.AdminToken))

	r.Post("/shorten", urlMappingHandler.Shorten)
	r.Get("/urls", urlMappingHandler.ListAll)
	r.Get("/{code}", urlMappingHandler.Resolve)
	r.Delete("/{code}", urlMappingHandler.Delete)
	server := &http.Server{
		Addr:         fmt.Sprintf(":%d", cfg.Port),
		Handler:      r,
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
