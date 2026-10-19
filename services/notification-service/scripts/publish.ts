import { randomUUID } from 'node:crypto';
import { connect } from 'amqplib';
import { config } from '../src/config';
import { eventSchema } from '../src/events';
import { setupTopology, topology } from '../src/topology';
async function main() {
  const type = process.argv[2];
  const base = {eventId: process.env.EVENT_ID || randomUUID(), transactionId: randomUUID(), email: process.env.DEMO_EMAIL || 'student@example.com', fullName: 'Nguyễn Văn An'};
  const event = eventSchema.parse(type === 'OtpRequested'
    ? {...base, type, otp: '123456', expiresAt: new Date(Date.now() + 300000).toISOString()}
    : {...base, type, studentId: '52300001', amount: 15000000, completedAt: new Date().toISOString()});
  const connection = await connect(config.RABBITMQ_URL);
  try {
    const ch = await connection.createConfirmChannel();
    await setupTopology(ch, config.RETRY_DELAY_MS);
    ch.publish(topology.exchange, event.type, Buffer.from(JSON.stringify(event)), {
      persistent: true, contentType: 'application/json', messageId: event.eventId,
    });
    await ch.waitForConfirms();
    console.log(`Published ${event.type}; eventId=${event.eventId}`);
    await ch.close();
  } finally { await connection.close(); }
}
main().catch(() => { console.error('Publish failed. Check RabbitMQ and event configuration.'); process.exitCode = 1; });
