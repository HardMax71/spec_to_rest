import { z } from 'zod';

export const UrlMappingCreateSchema = z.object({
  code: z.string(),
  url: z.string(),
  createdAt: z.coerce.date(),
  clickCount: z.number(),
});

export type UrlMappingCreate = z.infer<typeof UrlMappingCreateSchema>;

export const ShortenRequestSchema = z.object({
  url: z.string(),
});

export type ShortenRequest = z.infer<typeof ShortenRequestSchema>;

