package rest

import "github.com/grupomariposa/product-catalog/internal/domain"

// productDTO is the wire representation of a product. It mirrors the OpenAPI
// schema and is deliberately separate from domain.Product so transport changes
// never leak into the core.
type productDTO struct {
	ProductID   string `json:"productId"`
	Name        string `json:"name"`
	Description string `json:"description,omitempty"`
	Price       int64  `json:"price"`
	Currency    string `json:"currency"`
	Active      bool   `json:"active"`
}

func toProductDTO(p domain.Product) productDTO {
	return productDTO{
		ProductID:   p.ID,
		Name:        p.Name,
		Description: p.Description,
		Price:       p.PriceMinor,
		Currency:    p.Currency,
		Active:      p.Active,
	}
}

type batchGetRequest struct {
	IDs []string `json:"ids"`
}

type batchGetResponse struct {
	Products []productDTO `json:"products"`
	NotFound []string     `json:"notFound"`
}
