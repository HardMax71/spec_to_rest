package services

import (
	"context"
	"errors"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/generated/url-shortener/internal/config"
	"github.com/generated/url-shortener/internal/models"
)

type UrlMappingService struct {
	pool *pgxpool.Pool
	cfg  *config.Config
}

func NewUrlMappingService(pool *pgxpool.Pool, cfg *config.Config) *UrlMappingService {
	return &UrlMappingService{pool: pool, cfg: cfg}
}








// TODO: implement Shorten; the convention engine could not derive a
// CRUD shape for this operation, so the stub returns an explicit error to
// avoid silently reporting success.
func (s *UrlMappingService) Shorten(ctx context.Context, body models.ShortenRequest) error {
	_ = ctx
	_ = s
	return errors.New("Shorten not implemented")
}






func (s *UrlMappingService) ListAll(ctx context.Context) ([]models.UrlMappingRead, error) {
	rows, err := s.pool.Query(ctx, `SELECT id, code, url, created_at, click_count FROM url_mappings ORDER BY id`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := make([]models.UrlMappingRead, 0)
	for rows.Next() {
		var item models.UrlMappingRead
		if err := rows.Scan(&item.ID, &item.Code, &item.URL, &item.CreatedAt, &item.ClickCount); err != nil {
			return nil, err
		}
		out = append(out, item)
	}
	return out, rows.Err()
}











func (s *UrlMappingService) Resolve(ctx context.Context, code string) (*models.UrlMappingRead, error) {
	out := &models.UrlMappingRead{}
	row := s.pool.QueryRow(ctx, `SELECT id, code, url, created_at, click_count FROM url_mappings WHERE code = $1`, code)
	if err := row.Scan(&out.ID, &out.Code, &out.URL, &out.CreatedAt, &out.ClickCount); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	return out, nil
}








func (s *UrlMappingService) Delete(ctx context.Context, code string) (bool, error) {
	tag, err := s.pool.Exec(ctx, `DELETE FROM url_mappings WHERE code = $1`, code)
	if err != nil {
		return false, err
	}
	return tag.RowsAffected() > 0, nil
}





