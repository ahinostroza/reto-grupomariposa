package rest

import "net/http"

// NewRouter wires the catalog routes using the stdlib ServeMux method+path
// patterns (Go 1.22+). No third-party router is needed for this surface.
func NewRouter(h *Handler) *http.ServeMux {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /v1/products/{productId}", h.GetProduct)
	mux.HandleFunc("POST /v1/products:batchGet", h.BatchGetProducts)
	mux.HandleFunc("GET /openapi.yaml", ServeOpenAPISpec)
	mux.HandleFunc("GET /docs", ServeSwaggerUI)
	return mux
}
