import { app } from './app.js';
import { config } from './config.js';
import { prisma } from './prisma.js';

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
