package handlers

import (
	"encoding/json"
	"errors"
	"net/http"

	"github.com/generated/url-shortener/internal/models"
	"github.com/generated/url-shortener/internal/services"
	"github.com/go-chi/chi/v5"
)

type UrlMappingHandler struct {
	svc *services.UrlMappingService
}

func NewUrlMappingHandler(svc *services.UrlMappingService) *UrlMappingHandler {
	return &UrlMappingHandler{svc: svc}
}

func (h *UrlMappingHandler) Shorten(w http.ResponseWriter, r *http.Request) {
	var body models.ShortenRequest
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	if err := h.svc.Shorten(r.Context(), body); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	w.WriteHeader(201)
}

func (h *UrlMappingHandler) ListAll(w http.ResponseWriter, r *http.Request) {
	limit, offset := listPagination(r)
	result, err := h.svc.ListAll(r.Context(), limit, offset)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, 200, result)
}

func (h *UrlMappingHandler) Resolve(w http.ResponseWriter, r *http.Request) {
	code := chi.URLParam(r, "code")
	result, err := h.svc.Resolve(r.Context(), code)
	if err != nil {
		if errors.Is(err, services.ErrNotFound) {
			writeError(w, http.StatusNotFound, "not found")
			return
		}
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	http.Redirect(w, r, result.URL, 302)
}

func (h *UrlMappingHandler) Delete(w http.ResponseWriter, r *http.Request) {
	code := chi.URLParam(r, "code")
	ok, err := h.svc.Delete(r.Context(), code)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if !ok {
		writeError(w, http.StatusNotFound, "not found")
		return
	}
	w.WriteHeader(204)
}
