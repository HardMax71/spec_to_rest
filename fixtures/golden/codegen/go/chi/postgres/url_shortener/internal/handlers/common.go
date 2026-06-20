package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/generated/url-shortener/internal/models"
)

// queryInt reads an integer query parameter, falling back to def and clamping to [min, max].
func queryInt(r *http.Request, name string, def, min, max int) int {
	n := def
	if v := r.URL.Query().Get(name); v != "" {
		if parsed, err := strconv.Atoi(v); err == nil {
			n = parsed
		}
	}
	if n < min {
		return min
	}
	if n > max {
		return max
	}
	return n
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, detail string) {
	writeJSON(w, status, models.ErrorResponse{Detail: detail})
}
