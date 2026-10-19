import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { parseEvent } from '../src/events';
for (const file of ['otp.json', 'payment.json']) {
  const path = resolve('../payment-service/target/notification-contract', file);
  const event = parseEvent(readFileSync(path));
  console.log(`Java -> TypeScript contract OK: ${event.type}`);
}
