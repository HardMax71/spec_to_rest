import { prisma } from '../prisma.js';
import type {
  UrlMapping,
  UrlMappingCreate,
  UrlMappingRead,
  ShortenRequest,
} from '../types/urlMapping.js';

export type {
  UrlMapping,
  UrlMappingCreate,
  UrlMappingRead,
  ShortenRequest,
} from '../types/urlMapping.js';

// TODO: implement shorten; the convention engine could not derive a
// CRUD shape, so the stub throws to avoid silently reporting success.
export const shorten = async (body: ShortenRequest): Promise<void> => {
  void body;
  throw new Error('shorten not implemented');
};

export const listAll = async (): Promise<UrlMappingRead[]> => {
  return prisma.urlMapping.findMany({
    orderBy: { id: 'asc' },
  }) as unknown as Promise<UrlMappingRead[]>;
};

// TODO: implement resolve; a redirect operation carries spec side-effects the
// convention engine cannot derive (e.g. click-count increment), so the stub throws to
// avoid silently reporting success — matching the fastapi target.
export const resolve = async (code: string): Promise<UrlMappingRead | null> => {
  throw new Error('resolve not implemented');
};

export const delete_ = async (code: string): Promise<boolean> => {
  const result = await prisma.urlMapping.deleteMany({
    where: { code: code },
  });
  return result.count > 0;
};

