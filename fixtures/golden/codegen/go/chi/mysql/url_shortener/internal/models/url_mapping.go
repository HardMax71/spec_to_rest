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

var urlMappingCreateCodePattern = regexp.MustCompile(`^(?:^[a-zA-Z0-9]+$)$`)
var urlMappingCreateURLPattern = regexp.MustCompile(`^(?:^https?://[^\s\x00-\x1f\x7f]+)$`)

func (b UrlMappingCreate) Validate() error {
	if utf8.RuneCountInString(b.Code) < 6 {
		return fmt.Errorf("code: shorter than minimum length 6")
	}
	if !urlMappingCreateCodePattern.MatchString(b.Code) {
		return fmt.Errorf("code: does not match the required pattern")
	}
	if utf8.RuneCountInString(b.URL) < 1 {
		return fmt.Errorf("url: shorter than minimum length 1")
	}
	if !urlMappingCreateURLPattern.MatchString(b.URL) {
		return fmt.Errorf("url: does not match the required pattern")
	}
	return nil
}

var shortenRequestURLPattern = regexp.MustCompile(`^(?:^https?://[^\s\x00-\x1f\x7f]+)$`)

func (b ShortenRequest) Validate() error {
	if utf8.RuneCountInString(b.URL) < 1 {
		return fmt.Errorf("url: shorter than minimum length 1")
	}
	if !shortenRequestURLPattern.MatchString(b.URL) {
		return fmt.Errorf("url: does not match the required pattern")
	}
	return nil
}
