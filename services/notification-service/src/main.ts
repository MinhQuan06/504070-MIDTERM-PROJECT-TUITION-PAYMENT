import 'reflect-metadata';
import { Module } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { EmailService } from './email';
import { DedupeStore } from './dedupe';
import { NotificationWorker } from './worker';
@Module({providers: [EmailService, DedupeStore, NotificationWorker]})
class AppModule {}
async function bootstrap() {
  const app = await NestFactory.createApplicationContext(AppModule);
  app.enableShutdownHooks();
}
bootstrap().catch(() => { console.error('Notification service failed to start; check configuration.'); process.exitCode = 1; });
