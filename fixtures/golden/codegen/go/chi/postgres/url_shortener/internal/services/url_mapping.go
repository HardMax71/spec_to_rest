package services

import (
	"context"
	"database/sql"
	"errors"

	"github.com/uptrace/bun"

	"github.com/generated/url-shortener/internal/config"
	"github.com/generated/url-shortener/internal/models"
)

type UrlMappingService struct {
	db  *bun.DB
	cfg *config.Config
}

func NewUrlMappingService(db *bun.DB, cfg *config.Config) *UrlMappingService {
	return &UrlMappingService{db: db, cfg: cfg}
}








// TODO: implement Shorten; the convention engine could not derive a
// CRUD shape for this operation, so the stub returns an explicit error to
// avoid silently reporting success.
func (s *UrlMappingService) Shorten(ctx context.Context, body models.ShortenRequest) error {
	_ = ctx
	_ = s
	return errors.New("Shorten not implemented")
}






func (s *UrlMappingService) ListAll(ctx context.Context) ([]models.UrlMapping, error) {
	items := make([]models.UrlMapping, 0)
	if err := s.db.NewSelect().Model(&items).Order("id").Scan(ctx); err != nil {
		return nil, err
	}
	return items, nil
}











func (s *UrlMappingService) Resolve(ctx context.Context, code string) (*models.UrlMapping, error) {
	m := new(models.UrlMapping)
	err := s.db.NewSelect().Model(m).Where("? = ?", bun.Ident("code"), code).Limit(1).Scan(ctx)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	return m, nil
}








func (s *UrlMappingService) Delete(ctx context.Context, code string) (bool, error) {
	res, err := s.db.NewDelete().Model((*models.UrlMapping)(nil)).Where("? = ?", bun.Ident("code"), code).Exec(ctx)
	if err != nil {
		return false, err
	}
	n, err := res.RowsAffected()
	if err != nil {
		return false, err
	}
	return n > 0, nil
}





