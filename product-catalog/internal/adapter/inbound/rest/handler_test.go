package rest_test

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/grupomariposa/product-catalog/internal/adapter/inbound/rest"
	"github.com/grupomariposa/product-catalog/internal/adapter/outbound/memory"
	"github.com/grupomariposa/product-catalog/internal/application"
)

func newServer() http.Handler {
	svc := application.NewProductService(memory.NewSeeded())
	return rest.NewRouter(rest.NewHandler(svc))
}

func TestGetProduct_Found(t *testing.T) {
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/v1/products/product-789", nil)

	newServer().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	if ct := rec.Header().Get("Content-Type"); ct != "application/json" {
		t.Errorf("Content-Type = %q, want application/json", ct)
	}
	if rec.Header().Get("ETag") == "" {
		t.Error("expected ETag header")
	}
	if cc := rec.Header().Get("Cache-Control"); cc != "public, max-age=300" {
		t.Errorf("Cache-Control = %q", cc)
	}

	var body map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if body["price"].(float64) != 99900 {
		t.Errorf("price = %v, want 99900 (minor units)", body["price"])
	}
}

func TestGetProduct_NotFoundReturnsProblem(t *testing.T) {
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/v1/products/does-not-exist", nil)

	newServer().ServeHTTP(rec, req)

	if rec.Code != http.StatusNotFound {
		t.Fatalf("status = %d, want 404", rec.Code)
	}
	if ct := rec.Header().Get("Content-Type"); ct != "application/problem+json" {
		t.Errorf("Content-Type = %q, want application/problem+json", ct)
	}
}

func TestGetProduct_ConditionalReturns304(t *testing.T) {
	srv := newServer()

	first := httptest.NewRecorder()
	srv.ServeHTTP(first, httptest.NewRequest(http.MethodGet, "/v1/products/product-789", nil))
	etag := first.Header().Get("ETag")
	if etag == "" {
		t.Fatal("expected ETag on first response")
	}

	second := httptest.NewRecorder()
	condReq := httptest.NewRequest(http.MethodGet, "/v1/products/product-789", nil)
	condReq.Header.Set("If-None-Match", etag)
	srv.ServeHTTP(second, condReq)

	if second.Code != http.StatusNotModified {
		t.Fatalf("status = %d, want 304", second.Code)
	}
}

func TestBatchGet_PartialSuccess(t *testing.T) {
	rec := httptest.NewRecorder()
	body, _ := json.Marshal(map[string][]string{"ids": {"product-789", "ghost", "product-123"}})
	req := httptest.NewRequest(http.MethodPost, "/v1/products:batchGet", bytes.NewReader(body))

	newServer().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}

	var resp struct {
		Products []map[string]any `json:"products"`
		NotFound []string         `json:"notFound"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if len(resp.Products) != 2 {
		t.Errorf("products = %d, want 2", len(resp.Products))
	}
	if len(resp.NotFound) != 1 || resp.NotFound[0] != "ghost" {
		t.Errorf("notFound = %v, want [ghost]", resp.NotFound)
	}
}

func TestBatchGet_EmptyIDsRejected(t *testing.T) {
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/v1/products:batchGet", strings.NewReader(`{"ids":[]}`))

	newServer().ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400", rec.Code)
	}
}
