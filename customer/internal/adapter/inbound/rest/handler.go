package rest

import (
	"crypto/sha1"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"

	"github.com/grupomariposa/customer/internal/application"
	"github.com/grupomariposa/customer/internal/domain"
)

const (
	errBase = "https://errors.grupomariposa.com/"
	// Shorter TTL than the catalog: a customer block must take effect quickly.
	customerCacheCtl = "public, max-age=60"
)

// Handler exposes the customer use cases over HTTP.
type Handler struct {
	svc *application.CustomerService
}

func NewHandler(svc *application.CustomerService) *Handler {
	return &Handler{svc: svc}
}

func (h *Handler) GetCustomer(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("customerId")
	if id == "" {
		writeProblem(w, http.StatusBadRequest, errBase+"invalid-request",
			"Invalid request", "customerId must not be empty.", r.URL.Path)
		return
	}

	c, err := h.svc.Get(r.Context(), id)
	switch {
	case errors.Is(err, domain.ErrCustomerNotFound):
		// Business outcome: the client must not retry this.
		writeProblem(w, http.StatusNotFound, errBase+"customer-not-found",
			"Customer not found", fmt.Sprintf("Customer %q does not exist.", id), r.URL.Path)
		return
	case err != nil:
		// Treated as transient so the caller can retry with backoff.
		writeProblem(w, http.StatusServiceUnavailable, errBase+"upstream-unavailable",
			"Service unavailable", "Customer store is temporarily unavailable.", r.URL.Path)
		return
	}

	dto := toCustomerDTO(c)
	etag := etagOf(dto)

	if match := r.Header.Get("If-None-Match"); match != "" && match == etag {
		w.Header().Set("ETag", etag)
		w.WriteHeader(http.StatusNotModified)
		return
	}

	w.Header().Set("ETag", etag)
	w.Header().Set("Cache-Control", customerCacheCtl)
	writeJSON(w, http.StatusOK, dto)
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

func etagOf(v any) string {
	b, _ := json.Marshal(v)
	sum := sha1.Sum(b)
	return `"` + hex.EncodeToString(sum[:]) + `"`
}
