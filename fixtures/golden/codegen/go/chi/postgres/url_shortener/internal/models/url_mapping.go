package models

import (
	"time"

	"github.com/uptrace/bun"
)

type UrlMapping struct {
	bun.BaseModel `bun:"table:url_mappings,alias:url_mappings"`

	ID int64 `bun:"id,pk,autoincrement" json:"id"`

	Code string `bun:"code,notnull" json:"code"`

	URL string `bun:"url,notnull" json:"url"`

	CreatedAt time.Time `bun:"created_at,notnull" json:"created_at"`

	ClickCount int64 `bun:"click_count,notnull" json:"click_count"`

}

type UrlMappingCreate struct {

	Code string `json:"code" validate:"required"`

	URL string `json:"url" validate:"required"`

	CreatedAt time.Time `json:"created_at" validate:"required"`

	ClickCount int64 `json:"click_count" validate:"required"`

}


type ShortenRequest struct {

	URL string `json:"url" validate:"required"`

}


type ErrorResponse struct {
	Detail string `json:"detail"`
}
