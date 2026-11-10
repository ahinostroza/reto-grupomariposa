package rest

import (
	"crypto/sha1"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"

	"github.com/grupomariposa/product-catalog/internal/application"
	"github.com/grupomariposa/product-catalog/internal/domain"
)

const (
	errBase         = "https://errors.grupomariposa.com/"
	maxBatchIDs     = 100
	productCacheCtl = "public, max-age=300"
)

// Handler exposes the catalog use cases over HTTP.
type Handler struct {
	svc *application.ProductService
}

func NewHandler(svc *application.ProductService) *Handler {
	return &Handler{svc: svc}
}

func (h *Handler) GetProduct(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("productId")
	if id == "" {
		writeProblem(w, http.StatusBadRequest, errBase+"invalid-request",
			"Invalid request", "productId must not be empty.", r.URL.Path)
		return
	}

	p, err := h.svc.Get(r.Context(), id)
	switch {
	case errors.Is(err, domain.ErrProductNotFound):
		// Business outcome: the client must not retry this.
		writeProblem(w, http.StatusNotFound, errBase+"product-not-found",
			"Product not found", fmt.Sprintf("Product %q does not exist.", id), r.URL.Path)
		return
	case err != nil:
		// Treated as transient so the caller can retry with backoff.
		writeProblem(w, http.StatusServiceUnavailable, errBase+"upstream-unavailable",
			"Service unavailable", "Catalog store is temporarily unavailable.", r.URL.Path)
		return
	}

	dto := toProductDTO(p)
	etag := etagOf(dto)

	// Conditional GET: let clients (and caches) revalidate cheaply.
	if match := r.Header.Get("If-None-Match"); match != "" && match == etag {
		w.Header().Set("ETag", etag)
		w.WriteHeader(http.StatusNotModified)
		return
	}

	w.Header().Set("ETag", etag)
	w.Header().Set("Cache-Control", productCacheCtl)
	writeJSON(w, http.StatusOK, dto)
}

func (h *Handler) BatchGetProducts(w http.ResponseWriter, r *http.Request) {
	var req batchGetRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeProblem(w, http.StatusBadRequest, errBase+"invalid-request",
			"Invalid request", "Malformed JSON body.", r.URL.Path)
		return
	}
	switch {
	case len(req.IDs) == 0:
		writeProblem(w, http.StatusBadRequest, errBase+"invalid-request",
			"Invalid request", "ids must contain at least one entry.", r.URL.Path)
		return
	case len(req.IDs) > maxBatchIDs:
		writeProblem(w, http.StatusBadRequest, errBase+"invalid-request",
			"Invalid request", fmt.Sprintf("ids must not exceed %d entries.", maxBatchIDs), r.URL.Path)
		return
	}

	result, err := h.svc.BatchGet(r.Context(), req.IDs)
	if err != nil {
		writeProblem(w, http.StatusServiceUnavailable, errBase+"upstream-unavailable",
			"Service unavailable", "Catalog store is temporarily unavailable.", r.URL.Path)
		return
	}

	resp := batchGetResponse{
		Products: make([]productDTO, 0, len(result.Found)),
		NotFound: result.NotFound,
	}
	for _, p := range result.Found {
		resp.Products = append(resp.Products, toProductDTO(p))
	}
	writeJSON(w, http.StatusOK, resp)
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

// etagOf derives a strong ETag from the serialized payload.
func etagOf(v any) string {
	b, _ := json.Marshal(v)
	sum := sha1.Sum(b)
	return `"` + hex.EncodeToString(sum[:]) + `"`
}
