package application

import (
	"context"

	"github.com/grupomariposa/customer/internal/domain"
)

// CustomerRepository is the outbound port for customer retrieval. The core
// depends on this abstraction, never on a concrete datastore.
type CustomerRepository interface {
	FindByID(ctx context.Context, id string) (domain.Customer, error)
}
