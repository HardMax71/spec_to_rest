package handlers

import (
	"encoding/json"
	"net/http"

	"github.com/generated/url-shortener/internal/models"
)

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, detail string) {
	writeJSON(w, status, models.ErrorResponse{Detail: detail})
}
