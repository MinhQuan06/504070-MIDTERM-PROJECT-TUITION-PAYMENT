import { Inject, Injectable, Logger, OnModuleInit, OnApplicationShutdown } from '@nestjs/common';
import { connect, AmqpConnectionManager, ChannelWrapper } from 'amqp-connection-manager';
import type { ConfirmChannel, ConsumeMessage } from 'amqplib';
import { config } from './config';
import { EmailService } from './email';
import { DedupeStore } from './dedupe';
import { PermanentError, parseEvent } from './events';
import { setupTopology, topology } from './topology';
@Injectable()
export class NotificationWorker implements OnModuleInit, OnApplicationShutdown {
  private readonly logger = new Logger(NotificationWorker.name);
  private connection!: AmqpConnectionManager;
  private publisher!: ChannelWrapper;
  private consumer!: ChannelWrapper;
  constructor(@Inject(EmailService) private readonly email: EmailService, @Inject(DedupeStore) private readonly dedupe: DedupeStore) {}
  async onModuleInit() {
    this.connection = connect([config.RABBITMQ_URL], {heartbeatIntervalInSeconds: 5, reconnectTimeInSeconds: 5});
    this.connection.on('connect', () => this.logger.log('RabbitMQ connected'));
    this.connection.on('disconnect', () => this.logger.warn('RabbitMQ disconnected; reconnecting'));
    this.connection.on('connectFailed', () => this.logger.warn('RabbitMQ unavailable; retrying connection'));
    this.publisher = this.connection.createChannel({setup: (ch: ConfirmChannel) => setupTopology(ch, config.RETRY_DELAY_MS)});
    this.publisher.on('error', () => this.logger.error('Publisher channel setup failed; check queue configuration'));
    this.consumer = this.connection.createChannel({setup: async (ch: ConfirmChannel) => {
      await setupTopology(ch, config.RETRY_DELAY_MS);
      // One in-flight message per worker prevents duplicate deliveries racing locally.
      await ch.prefetch(1);
      await ch.consume(topology.queue, msg => { if (msg) void this.handle(ch, msg); }, {noAck: false});
      this.logger.log('Listening on notification.email');
    }});
    this.consumer.on('error', () => this.logger.error('Consumer channel setup failed; check queue configuration'));
  }
  async handle(channel: ConfirmChannel, msg: ConsumeMessage): Promise<void> {
    try {
      const event = parseEvent(msg.content);
      if (!await this.dedupe.has(event.eventId)) {
        await this.email.send(event);
        await this.dedupe.mark(event.eventId);
        this.logger.log(`Sent type=${event.type} eventId=${event.eventId}`);
      } else this.logger.log(`Duplicate skipped eventId=${event.eventId}`);
      channel.ack(msg);
    } catch (error) {
      const raw = Number(msg.properties.headers?.['x-retry-count'] ?? 0);
      const retries = Number.isInteger(raw) && raw >= 0 ? raw : config.MAX_RETRIES;
      const permanent = error instanceof PermanentError;
      const destination = permanent || retries >= config.MAX_RETRIES ? topology.dlq : topology.retry;
      try {
        await this.publisher.sendToQueue(destination, msg.content, {
          persistent: true, timeout: 10000, contentType: 'application/json', messageId: msg.properties.messageId,
          headers: {'x-retry-count': retries + 1, 'x-failure-code': permanent ? error.message : 'DELIVERY_FAILED'},
        });
        channel.ack(msg);
        this.logger.warn(`Message moved to ${destination}; attempt=${retries + 1}`);
      } catch {
        this.logger.error('Cannot confirm recovery publish; requeue original');
        try { channel.nack(msg, false, true); } catch { /* Closed channel requeues unacked messages. */ }
      }
    }
  }
  async onApplicationShutdown() {
    await this.consumer?.close();
    await this.publisher?.close();
    await this.connection?.close();
  }
}
