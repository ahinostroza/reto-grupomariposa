package rest_test

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestServesEmbeddedOpenAPISpec(t *testing.T) {
	rec := httptest.NewRecorder()
	newServer().ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/openapi.yaml", nil))

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	if !strings.Contains(rec.Body.String(), "Customer API") {
		t.Error("expected the embedded OpenAPI contract in the response")
	}
}

func TestServesSwaggerUI(t *testing.T) {
	rec := httptest.NewRecorder()
	newServer().ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/docs", nil))

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	if !strings.Contains(rec.Body.String(), "swagger-ui") {
		t.Error("expected the Swagger UI page")
	}
}
