package application

import (
	"context"

	"github.com/grupomariposa/product-catalog/internal/domain"
)

// ProductService holds the catalog use cases (inbound side of the hexagon).
type ProductService struct {
	repo ProductRepository
}

func NewProductService(repo ProductRepository) *ProductService {
	return &ProductService{repo: repo}
}

// Get returns a single product, or domain.ErrProductNotFound when absent.
func (s *ProductService) Get(ctx context.Context, id string) (domain.Product, error) {
	return s.repo.FindByID(ctx, id)
}

// BatchResult carries partial-success semantics: the products that exist and
// the ids that do not. The caller owns the business rule (e.g. "all must exist")
// instead of the service failing the whole batch for one missing id.
type BatchResult struct {
	Found    []domain.Product
	NotFound []string
}

// BatchGet resolves many ids in a single round trip. Duplicate ids are collapsed
// and reported at most once in NotFound.
func (s *ProductService) BatchGet(ctx context.Context, ids []string) (BatchResult, error) {
	found, err := s.repo.FindByIDs(ctx, ids)
	if err != nil {
		return BatchResult{}, err
	}

	present := make(map[string]struct{}, len(found))
	for _, p := range found {
		present[p.ID] = struct{}{}
	}

	notFound := make([]string, 0)
	seen := make(map[string]struct{}, len(ids))
	for _, id := range ids {
		if _, dup := seen[id]; dup {
			continue
		}
		seen[id] = struct{}{}
		if _, ok := present[id]; !ok {
			notFound = append(notFound, id)
		}
	}

	return BatchResult{Found: found, NotFound: notFound}, nil
}
