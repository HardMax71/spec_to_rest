package models


import "time"




type UrlMapping struct {

	ID int64 `json:"id" db:"id"`

	Code string `json:"code" db:"code"`

	URL string `json:"url" db:"url"`

	CreatedAt time.Time `json:"created_at" db:"created_at"`

	ClickCount int64 `json:"click_count" db:"click_count"`

}

type UrlMappingCreate struct {

	Code string `json:"code" validate:"required"`

	URL string `json:"url" validate:"required"`

	CreatedAt time.Time `json:"created_at" validate:"required"`

	ClickCount int64 `json:"click_count" validate:"required"`

}

type UrlMappingRead struct {

	ID int64 `json:"id"`

	Code string `json:"code"`

	URL string `json:"url"`

	CreatedAt time.Time `json:"created_at"`

	ClickCount int64 `json:"click_count"`

}


type ShortenRequest struct {

	URL string `json:"url" validate:"required"`

}


type ErrorResponse struct {
	Detail string `json:"detail"`
}
