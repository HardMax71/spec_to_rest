package models

import (
	"fmt"
	"regexp"
	"time"
	"unicode/utf8"

	"github.com/uptrace/bun"
)

type UrlMapping struct {
	bun.BaseModel `bun:"table:url_mappings,alias:url_mappings"`
	ID            int64     `bun:"id,pk,autoincrement" json:"id"`
	Code          string    `bun:"code,notnull" json:"code"`
	URL           string    `bun:"url,notnull" json:"url"`
	CreatedAt     time.Time `bun:"created_at,notnull" json:"created_at"`
	ClickCount    int64     `bun:"click_count,notnull" json:"click_count"`
}

type UrlMappingCreate struct {
	Code       string    `json:"code" validate:"required"`
	URL        string    `json:"url" validate:"required"`
	CreatedAt  time.Time `json:"created_at" validate:"required"`
	ClickCount int64     `json:"click_count" validate:"required"`
}
type ShortenRequest struct {
	URL string `json:"url" validate:"required"`
}

var urlMappingValidationPattern1 = regexp.MustCompile(`^(?:^https?://[^\s\x00-\x1f\x7f]+)$`)

func (b ShortenRequest) Validate() error {
	if utf8.RuneCountInString(b.URL) < 1 {
		return fmt.Errorf("url: shorter than minimum length 1")
	}
	if !urlMappingValidationPattern1.MatchString(b.URL) {
		return fmt.Errorf("url: does not match the required pattern")
	}
	return nil
}
