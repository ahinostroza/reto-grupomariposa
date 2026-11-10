package memory

import (
	"context"
	"sync"

	"github.com/grupomariposa/product-catalog/internal/application"
	"github.com/grupomariposa/product-catalog/internal/domain"
)

// Repository is an in-memory ProductRepository. It stands in for a real
// datastore for this challenge; because it sits behind the ProductRepository
// port, replacing it with Postgres/Mongo later touches neither the domain nor
// the handlers.
type Repository struct {
	mu       sync.RWMutex
	products map[string]domain.Product
}

// Compile-time guarantee that Repository satisfies the port.
var _ application.ProductRepository = (*Repository)(nil)

func New() *Repository {
	return &Repository{products: make(map[string]domain.Product)}
}

// NewSeeded returns a repository preloaded with sample catalog data.
func NewSeeded() *Repository {
	r := New()
	for _, p := range seed() {
		r.products[p.ID] = p
	}
	return r
}

func (r *Repository) FindByID(_ context.Context, id string) (domain.Product, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	p, ok := r.products[id]
	if !ok {
		return domain.Product{}, domain.ErrProductNotFound
	}
	return p, nil
}

func (r *Repository) FindByIDs(_ context.Context, ids []string) ([]domain.Product, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	out := make([]domain.Product, 0, len(ids))
	seen := make(map[string]struct{}, len(ids))
	for _, id := range ids {
		if _, dup := seen[id]; dup {
			continue
		}
		seen[id] = struct{}{}
		if p, ok := r.products[id]; ok {
			out = append(out, p)
		}
	}
	return out, nil
}

func seed() []domain.Product {
	return []domain.Product{
		{ID: "product-789", Name: "Laptop", Description: "14-inch ultrabook, 16GB RAM", PriceMinor: 99900, Currency: "USD", Active: true},
		{ID: "product-123", Name: "Wireless Mouse", Description: "Ergonomic 2.4GHz mouse", PriceMinor: 2599, Currency: "USD", Active: true},
		{ID: "product-456", Name: "Mechanical Keyboard", Description: "Hot-swappable switches, RGB", PriceMinor: 8999, Currency: "USD", Active: true},
		{ID: "product-999", Name: "Legacy Adapter", Description: "Discontinued accessory", PriceMinor: 1500, Currency: "USD", Active: false},
	}
}
