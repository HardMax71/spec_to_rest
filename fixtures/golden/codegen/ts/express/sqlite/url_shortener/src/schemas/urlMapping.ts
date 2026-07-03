import { z } from 'zod';

export const UrlMappingCreateSchema = z.object({
  code: z.string().min(6).regex(/^(?:^[a-zA-Z0-9]+$)$/),
  url: z.string().min(1).regex(/^(?:^https?:\/\/[^\s\x00-\x1f\x7f]+)$/),
  createdAt: z.coerce.date(),
  clickCount: z.number(),
});

export type UrlMappingCreate = z.infer<typeof UrlMappingCreateSchema>;

export const ShortenRequestSchema = z.object({
  url: z.string().min(1).regex(/^(?:^https?:\/\/[^\s\x00-\x1f\x7f]+)$/),
});

export type ShortenRequest = z.infer<typeof ShortenRequestSchema>;

