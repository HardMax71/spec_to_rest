package extensions

import (
	"github.com/go-chi/chi/v5"
	"github.com/uptrace/bun"
)

// Register lets you mount custom routes and middleware on top of the
// spec-derived ones. This file is never overwritten by `spec-to-rest
// compile`; the generated cmd/server/main.go calls Register once after
// mounting all spec-derived handlers.
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
