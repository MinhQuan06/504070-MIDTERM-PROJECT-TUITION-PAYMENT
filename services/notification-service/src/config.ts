import 'dotenv/config';
import { z } from 'zod';
const boolean = z.enum(['true', 'false']).transform(v => v === 'true');
const schema = z.object({
  RABBITMQ_URL: z.string().url().optional(),
  RABBITMQ_HOST: z.string().min(1).default('localhost'),
  RABBITMQ_PORT: z.coerce.number().int().min(1).max(65535).default(5672),
  RABBITMQ_USER: z.string().min(1).default('notification'),
  RABBITMQ_PASSWORD: z.string().min(1).default('notification_dev'),
  SMTP_HOST: z.string().min(1).default('localhost'),
  SMTP_PORT: z.coerce.number().int().min(1).max(65535).default(1025),
  SMTP_SECURE: boolean.default('false'), SMTP_REQUIRE_TLS: boolean.default('false'),
  SMTP_USER: z.string().default(''), SMTP_PASSWORD: z.string().default(''),
  MAIL_FROM: z.string().min(1).default('iBanking TDTU <no-reply@ibanking.local>'),
  MAX_RETRIES: z.coerce.number().int().min(0).max(10).default(3),
  RETRY_DELAY_MS: z.coerce.number().int().min(100).default(5000),
  DEDUPE_DIR: z.string().min(1).default('./data/sent'),
}).superRefine((v, ctx) => {
  if (Boolean(v.SMTP_USER) !== Boolean(v.SMTP_PASSWORD)) ctx.addIssue({code: 'custom', message: 'Set both SMTP_USER and SMTP_PASSWORD'});
});
const parsed = schema.parse(process.env);
const amqpUrl = new URL('amqp://localhost');
amqpUrl.hostname = parsed.RABBITMQ_HOST;
amqpUrl.port = String(parsed.RABBITMQ_PORT);
amqpUrl.username = parsed.RABBITMQ_USER;
amqpUrl.password = parsed.RABBITMQ_PASSWORD;
export const config = {...parsed, RABBITMQ_URL: parsed.RABBITMQ_URL ?? amqpUrl.toString()};
