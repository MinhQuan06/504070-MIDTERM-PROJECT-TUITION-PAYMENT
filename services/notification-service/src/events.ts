import { z } from 'zod';
const id = z.string().min(1).max(128);
const base = { eventId: id, transactionId: id, email: z.string().email(), fullName: z.string().min(1).max(200) };
export const eventSchema = z.discriminatedUnion('type', [
  z.object({ ...base, type: z.literal('OtpRequested'), otp: z.string().regex(/^\d{6}$/), expiresAt: z.string().datetime({offset: true}) }),
  z.object({ ...base, type: z.literal('PaymentSucceeded'), studentId: id, amount: z.number().int().positive().safe(), completedAt: z.string().datetime({offset: true}) }),
]);
export type NotificationEvent = z.infer<typeof eventSchema>;
export class PermanentError extends Error {}
export function parseEvent(body: Buffer): NotificationEvent {
  try { return eventSchema.parse(JSON.parse(body.toString('utf8'))); }
  catch { throw new PermanentError('INVALID_EVENT'); }
}
export function ensureFresh(event: NotificationEvent, now = Date.now()): void {
  if (event.type === 'OtpRequested' && Date.parse(event.expiresAt) <= now) throw new PermanentError('OTP_EXPIRED');
}
