package rest_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/grupomariposa/customer/internal/adapter/inbound/rest"
	"github.com/grupomariposa/customer/internal/adapter/outbound/memory"
	"github.com/grupomariposa/customer/internal/application"
)

func newServer() http.Handler {
	svc := application.NewCustomerService(memory.NewSeeded())
	return rest.NewRouter(rest.NewHandler(svc))
}

func TestGetCustomer_Active(t *testing.T) {
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/v1/customers/customer-456", nil)

	newServer().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	if cc := rec.Header().Get("Cache-Control"); cc != "public, max-age=60" {
		t.Errorf("Cache-Control = %q", cc)
	}

	var body map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if body["status"] != "ACTIVE" {
		t.Errorf("status = %v, want ACTIVE", body["status"])
	}
}

// The API reports state; it does not apply the business rule. A blocked customer
// is a valid 200 response carrying status BLOCKED — the worker decides to reject.
func TestGetCustomer_BlockedStillReturns200(t *testing.T) {
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/v1/customers/customer-111", nil)

	newServer().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	var body map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if body["status"] != "BLOCKED" {
		t.Errorf("status = %v, want BLOCKED", body["status"])
	}
}

func TestGetCustomer_NotFoundReturnsProblem(t *testing.T) {
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/v1/customers/does-not-exist", nil)

	newServer().ServeHTTP(rec, req)

	if rec.Code != http.StatusNotFound {
		t.Fatalf("status = %d, want 404", rec.Code)
	}
	if ct := rec.Header().Get("Content-Type"); ct != "application/problem+json" {
		t.Errorf("Content-Type = %q, want application/problem+json", ct)
	}
}

func TestGetCustomer_ConditionalReturns304(t *testing.T) {
	srv := newServer()

	first := httptest.NewRecorder()
	srv.ServeHTTP(first, httptest.NewRequest(http.MethodGet, "/v1/customers/customer-456", nil))
	etag := first.Header().Get("ETag")
	if etag == "" {
		t.Fatal("expected ETag on first response")
	}

	second := httptest.NewRecorder()
	condReq := httptest.NewRequest(http.MethodGet, "/v1/customers/customer-456", nil)
	condReq.Header.Set("If-None-Match", etag)
	srv.ServeHTTP(second, condReq)

	if second.Code != http.StatusNotModified {
		t.Fatalf("status = %d, want 304", second.Code)
	}
}
