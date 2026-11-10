package application

import (
	"context"

	"github.com/grupomariposa/customer/internal/domain"
)

// CustomerService holds the customer use cases.
type CustomerService struct {
	repo CustomerRepository
}

func NewCustomerService(repo CustomerRepository) *CustomerService {
	return &CustomerService{repo: repo}
}

// Get returns a single customer, or domain.ErrCustomerNotFound when absent.
func (s *CustomerService) Get(ctx context.Context, id string) (domain.Customer, error) {
	return s.repo.FindByID(ctx, id)
}
