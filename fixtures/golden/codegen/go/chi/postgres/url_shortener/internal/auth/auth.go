package auth

import (
	"crypto/subtle"
	"net/http"
	"strings"
)

const bearerPrefix = "Bearer "

// RequireAdmin guards a route with a bearer token. With no token configured
// (the production default) the guarded surface does not exist: every request
// gets 404. With one configured, requests need Authorization: Bearer <token>;
// the scheme match is case-insensitive (RFC 7235), like FastAPI's HTTPBearer.
func RequireAdmin(token string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if token == "" {
				http.NotFound(w, r)
				return
			}
			header := r.Header.Get("Authorization")
			presented := ""
			if len(header) >= len(bearerPrefix) && strings.EqualFold(header[:len(bearerPrefix)], bearerPrefix) {
				presented = header[len(bearerPrefix):]
			}
			if subtle.ConstantTimeCompare([]byte(presented), []byte(token)) != 1 {
				w.Header().Set("WWW-Authenticate", "Bearer")
				http.Error(w, "Unauthorized", http.StatusUnauthorized)
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}
