package extensions

import (
	"github.com/go-chi/chi/v5"
	"github.com/uptrace/bun"
)

// Register installs custom routes and middleware. This file is never
// overwritten by `spec-to-rest compile`.
//
// The generated cmd/server/main.go calls Register BEFORE wiring any
// spec-derived route — that ordering is mandatory because chi panics on
// r.Use(...) after a route has been registered ("chi: all middlewares
// must be defined before routes on a mux"). Middleware installed here
// therefore wraps every generated handler, and routes added here take
// precedence on path collisions (chi panics on duplicate registrations,
// so deliberately shadowing a generated path is not supported).
//
// Example:
//
//	func Register(r chi.Router, db *bun.DB) {
//		r.Get("/custom/ping", func(w http.ResponseWriter, _ *http.Request) {
//			_, _ = w.Write([]byte(`{"status":"ok"}`))
//		})
//	}
func Register(r chi.Router, db *bun.DB) {
	_ = r
	_ = db
}
