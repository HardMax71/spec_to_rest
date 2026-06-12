import type { Express } from 'express';

import { registerAdminRoutes } from './admin.js';

import { registerUrlMappingRoutes } from './urlMappings.js';


export const mountRoutes = (app: Express): void => {
  registerAdminRoutes(app);

  registerUrlMappingRoutes(app);

};
