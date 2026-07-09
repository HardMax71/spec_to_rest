
export interface UrlMapping {
  id: number;
  code: string;
  url: string;
  created_at: Date;
  click_count: number;
}

export interface UrlMappingCreate {
  code: string;
  url: string;
  created_at: Date;
  click_count: number;
}

export interface UrlMappingRead {
  id: number;
  code: string;
  url: string;
  created_at: Date;
  click_count: number;
}

export interface ShortenRequest {
  url: string;
}

export interface ErrorResponse {
  detail: string;
}
