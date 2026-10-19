import type { ConfirmChannel } from 'amqplib';
export const topology = {
  exchange: 'ibanking.events', queue: 'notification.email',
  retry: 'notification.email.retry', dlq: 'notification.email.dlq',
};
export async function setupTopology(channel: ConfirmChannel, delay: number): Promise<void> {
  await channel.assertExchange(topology.exchange, 'topic', {durable: true});
  await channel.assertQueue(topology.queue, {durable: true});
  await channel.assertQueue(topology.retry, {durable: true, arguments: {
    'x-message-ttl': delay, 'x-dead-letter-exchange': '', 'x-dead-letter-routing-key': topology.queue,
  }});
  await channel.assertQueue(topology.dlq, {durable: true});
  for (const key of ['OtpRequested', 'PaymentSucceeded']) await channel.bindQueue(topology.queue, topology.exchange, key);
}
