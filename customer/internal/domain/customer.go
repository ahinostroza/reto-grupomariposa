package domain

import "errors"

// ErrCustomerNotFound is returned when a customer id has no match.
var ErrCustomerNotFound = errors.New("customer not found")

// Status is the customer lifecycle state. Modeled as an enum rather than a
// boolean so new states (e.g. BLOCKED vs INACTIVE) do not force a schema change.
type Status string

const (
	StatusActive   Status = "ACTIVE"
	StatusBlocked  Status = "BLOCKED"
	StatusInactive Status = "INACTIVE"
)

// Customer is the core entity. Whether the customer may place orders is a
// business decision left to the consumer (the worker accepts ACTIVE only);
// this service only reports the state.
type Customer struct {
	ID     string
	Name   string
	Email  string
	Status Status
}
