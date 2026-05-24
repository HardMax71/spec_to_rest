package extensions

import (
	"github.com/go-chi/chi/v5"
	"github.com/uptrace/bun"
)

// Register installs custom routes and middleware. This file is never
// overwritten by `spec-to-rest compile`.
//
// The generated cmd/server/main.go calls Register BEFORE wiring any
// spec-derived route — required because chi panics on r.Use(...) after
// a route has been registered. Middleware installed here therefore
// wraps every generated handler. Routes added here are overwritten by
// any generated route with the same method+path (chi v5 silently
// replaces a duplicate; the spec wins on collision).
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
