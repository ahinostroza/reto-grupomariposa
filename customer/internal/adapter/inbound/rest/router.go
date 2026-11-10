package rest

import "net/http"

// NewRouter wires the customer routes using the stdlib ServeMux method+path
// patterns (Go 1.22+).
func NewRouter(h *Handler) *http.ServeMux {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /v1/customers/{customerId}", h.GetCustomer)
	mux.HandleFunc("GET /openapi.yaml", ServeOpenAPISpec)
	mux.HandleFunc("GET /docs", ServeSwaggerUI)
	return mux
}
