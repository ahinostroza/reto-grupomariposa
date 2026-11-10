package memory

import (
	"context"
	"sync"

	"github.com/grupomariposa/customer/internal/application"
	"github.com/grupomariposa/customer/internal/domain"
)

// Repository is an in-memory CustomerRepository standing in for a real store.
// It sits behind the port, so swapping it later touches nothing else.
type Repository struct {
	mu        sync.RWMutex
	customers map[string]domain.Customer
}

var _ application.CustomerRepository = (*Repository)(nil)

func New() *Repository {
	return &Repository{customers: make(map[string]domain.Customer)}
}

// NewSeeded returns a repository preloaded with sample customers covering every
// lifecycle state.
func NewSeeded() *Repository {
	r := New()
	for _, c := range seed() {
		r.customers[c.ID] = c
	}
	return r
}

func (r *Repository) FindByID(_ context.Context, id string) (domain.Customer, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	c, ok := r.customers[id]
	if !ok {
		return domain.Customer{}, domain.ErrCustomerNotFound
	}
	return c, nil
}

func seed() []domain.Customer {
	return []domain.Customer{
		{ID: "customer-456", Name: "Ada Lovelace", Email: "ada@example.com", Status: domain.StatusActive},
		{ID: "customer-111", Name: "Alan Turing", Email: "alan@example.com", Status: domain.StatusBlocked},
		{ID: "customer-222", Name: "Grace Hopper", Email: "grace@example.com", Status: domain.StatusInactive},
	}
}
