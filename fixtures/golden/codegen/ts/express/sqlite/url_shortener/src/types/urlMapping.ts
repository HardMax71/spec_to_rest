
export interface UrlMapping {
  id: number;
  code: string;
  url: string;
  createdAt: Date;
  clickCount: number;
}

export interface UrlMappingCreate {
  code: string;
  url: string;
  createdAt: Date;
  clickCount: number;
}

export interface UrlMappingRead {
  id: number;
  code: string;
  url: string;
  createdAt: Date;
  clickCount: number;
}

export interface ShortenRequest {
  url: string;
}

export interface ErrorResponse {
  detail: string;
}
