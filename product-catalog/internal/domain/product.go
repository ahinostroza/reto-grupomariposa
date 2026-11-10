package domain

import "errors"

// ErrProductNotFound is returned when a product id has no match in the catalog.
var ErrProductNotFound = errors.New("product not found")

// Product is the catalog's core entity.
//
// Price is kept in minor units (e.g. cents) as an integer: representing money
// with floating-point types leads to rounding errors (0.1 + 0.2 != 0.3).
type Product struct {
	ID          string
	Name        string
	Description string
	PriceMinor  int64
	Currency    string
	Active      bool
}
