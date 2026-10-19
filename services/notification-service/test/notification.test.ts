import 'reflect-metadata';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseEvent, ensureFresh, PermanentError } from '../src/events';
import { renderEmail } from '../src/email';
import { NotificationWorker } from '../src/worker';
import type { ConsumeMessage, ConfirmChannel } from 'amqplib';
const event = {eventId: 'e1', transactionId: 'tx1', email: 'test@example.com', fullName: '<script>alert(1)</script>', type: 'OtpRequested', otp: '123456', expiresAt: new Date(Date.now()+300000).toISOString()};
function harness(sendError?: Error, alreadySent = false, retry = 0, body = event) {
  const calls: string[] = [];
  const email = {send: async () => { calls.push('send'); if (sendError) throw sendError; }};
  const store = {has: async () => alreadySent, mark: async () => {calls.push('mark');}};
  const worker = new NotificationWorker(email as any, store as any);
  (worker as any).publisher = {sendToQueue: async (queue: string) => { calls.push(queue); }};
  const channel = {ack: () => calls.push('ack'), nack: () => calls.push('nack')} as unknown as ConfirmChannel;
  const msg = {content: Buffer.from(JSON.stringify(body)), properties: {headers: {'x-retry-count': retry}}} as unknown as ConsumeMessage;
  return {worker, channel, msg, calls};
}
test('invalid event is rejected without exposing payload', () => {
  assert.throws(() => parseEvent(Buffer.from('{')), PermanentError);
  assert.throws(() => parseEvent(Buffer.from(JSON.stringify({...event, otp:'bad'}))), PermanentError);
});
test('expired OTP is never sent', () => {
  const parsed = parseEvent(Buffer.from(JSON.stringify({...event, expiresAt: '2000-01-01T00:00:00Z'})));
  assert.throws(() => ensureFresh(parsed), /OTP_EXPIRED/);
});
test('templates escape untrusted HTML and include payment amount', () => {
  const mail = renderEmail(parseEvent(Buffer.from(JSON.stringify(event))));
  assert.ok(!mail.html.includes('<script>')); assert.ok(mail.html.includes('&lt;script&gt;'));
  const paid = renderEmail(parseEvent(Buffer.from(JSON.stringify({...event, type:'PaymentSucceeded', studentId:'52300001', amount:15000000, completedAt:new Date().toISOString()}))));
  assert.ok(paid.text.includes('15.000.000 VND'));
});
test('successful send is recorded before ack', async () => {
  const h = harness(); await h.worker.handle(h.channel,h.msg); assert.deepEqual(h.calls,['send','mark','ack']);
});
test('duplicate event is acknowledged without resending', async () => {
  const h = harness(undefined,true); await h.worker.handle(h.channel,h.msg); assert.deepEqual(h.calls,['ack']);
});
test('SMTP failure is confirmed to retry queue before ack', async () => {
  const h = harness(new Error('smtp unavailable')); await h.worker.handle(h.channel,h.msg); assert.deepEqual(h.calls,['send','notification.email.retry','ack']);
});
test('exhausted retries go to DLQ', async () => {
  const h = harness(new Error('smtp unavailable'),false,3); await h.worker.handle(h.channel,h.msg); assert.deepEqual(h.calls,['send','notification.email.dlq','ack']);
});
test('invalid payload goes directly to DLQ', async () => {
  const h = harness(undefined,false,0,{...event,otp:'bad'}); await h.worker.handle(h.channel,h.msg); assert.deepEqual(h.calls,['notification.email.dlq','ack']);
});
test('failed retry publish requeues original without ack', async () => {
  const h = harness(new Error('smtp unavailable'));
  (h.worker as any).publisher.sendToQueue = async () => {throw new Error('broker unavailable');};
  await h.worker.handle(h.channel,h.msg); assert.deepEqual(h.calls,['send','nack']);
});
