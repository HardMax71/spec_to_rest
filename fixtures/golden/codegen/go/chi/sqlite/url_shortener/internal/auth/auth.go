package auth

import (
	"crypto/subtle"
	"encoding/json"
	"net/http"
	"strings"
)

const bearerPrefix = "Bearer "

// writeDetail mirrors the {"detail": ...} error shape (ErrorResponse) the
// fastapi/express targets emit, so the admin contract is target-uniform.
func writeDetail(w http.ResponseWriter, status int, detail string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(map[string]string{"detail": detail})
}

// RequireAdmin guards a route with a bearer token. With no token configured
// (the production default) the guarded surface does not exist: every request
// gets 404. With one configured, requests need Authorization: Bearer <token>;
// the scheme match is case-insensitive (RFC 7235), like FastAPI's HTTPBearer.
func RequireAdmin(token string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if token == "" {
				writeDetail(w, http.StatusNotFound, "Not Found")
				return
			}
			header := r.Header.Get("Authorization")
			presented := ""
			if len(header) >= len(bearerPrefix) && strings.EqualFold(header[:len(bearerPrefix)], bearerPrefix) {
				presented = header[len(bearerPrefix):]
			}
			if subtle.ConstantTimeCompare([]byte(presented), []byte(token)) != 1 {
				w.Header().Set("WWW-Authenticate", "Bearer")
				writeDetail(w, http.StatusUnauthorized, "Unauthorized")
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}
