package application_test

import (
	"context"
	"testing"

	"github.com/grupomariposa/product-catalog/internal/adapter/outbound/memory"
	"github.com/grupomariposa/product-catalog/internal/application"
)

func TestBatchGet_ReportsMissingIDsOnce(t *testing.T) {
	svc := application.NewProductService(memory.NewSeeded())

	res, err := svc.BatchGet(context.Background(), []string{"ghost", "ghost", "product-789"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(res.Found) != 1 {
		t.Errorf("found = %d, want 1", len(res.Found))
	}
	if len(res.NotFound) != 1 || res.NotFound[0] != "ghost" {
		t.Errorf("notFound = %v, want [ghost] (deduplicated)", res.NotFound)
	}
}

func TestBatchGet_AllPresent(t *testing.T) {
	svc := application.NewProductService(memory.NewSeeded())

	res, err := svc.BatchGet(context.Background(), []string{"product-789", "product-123"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(res.Found) != 2 {
		t.Errorf("found = %d, want 2", len(res.Found))
	}
	if len(res.NotFound) != 0 {
		t.Errorf("notFound = %v, want empty", res.NotFound)
	}
}
