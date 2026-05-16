import type { Express } from 'express';


import { registerUrlMappingRoutes } from './urlMappings.js';


export const mountRoutes = (app: Express): void => {

  registerUrlMappingRoutes(app);

};
