import { app } from './app.js';
import { config } from './config.js';
import { prisma } from './prisma.js';

if (process.env.ENABLE_TEST_ADMIN === '1') {
  try {
    const adminModulePath: string = './routes/testAdmin.js';
    const mod = (await import(adminModulePath)) as {
      registerTestAdminRoutes: (a: typeof app) => void;
    };
    mod.registerTestAdminRoutes(app);
    console.log(JSON.stringify({ level: 'info', msg: 'test admin routes mounted' }));
  } catch (e) {
    console.log(
      JSON.stringify({ level: 'warn', msg: 'test admin module unavailable', error: String(e) }),
    );
  }
}

const server = app.listen(config.port, () => {
  console.log(JSON.stringify({ level: 'info', msg: 'server started', port: config.port }));
});

const shutdown = async (signal: string): Promise<void> => {
  console.log(JSON.stringify({ level: 'info', msg: 'shutdown requested', signal }));
  server.close(async () => {
    await prisma.$disconnect();
    process.exit(0);
  });
  setTimeout(() => process.exit(1), 10_000).unref();
};

process.on('SIGTERM', () => void shutdown('SIGTERM'));
process.on('SIGINT', () => void shutdown('SIGINT'));
