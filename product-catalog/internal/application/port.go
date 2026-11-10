package application

import (
	"context"

	"github.com/grupomariposa/product-catalog/internal/domain"
)

// ProductRepository is the outbound port for product retrieval. The application
// core depends on this interface, never on a concrete datastore, so the backing
// store (in-memory, SQL, Mongo, ...) can be swapped without touching the domain.
type ProductRepository interface {
	FindByID(ctx context.Context, id string) (domain.Product, error)
	FindByIDs(ctx context.Context, ids []string) ([]domain.Product, error)
}
