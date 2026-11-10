package rest

import "github.com/grupomariposa/customer/internal/domain"

// customerDTO is the wire representation, kept separate from domain.Customer.
type customerDTO struct {
	CustomerID string `json:"customerId"`
	Name       string `json:"name"`
	Email      string `json:"email,omitempty"`
	Status     string `json:"status"`
}

func toCustomerDTO(c domain.Customer) customerDTO {
	return customerDTO{
		CustomerID: c.ID,
		Name:       c.Name,
		Email:      c.Email,
		Status:     string(c.Status),
	}
}
