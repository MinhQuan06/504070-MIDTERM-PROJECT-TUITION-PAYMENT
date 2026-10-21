import { config } from './config';
import { PermanentError, type NotificationEvent } from './events';

// Fail closed on lookup outages: the worker retries without sending an unchecked OTP.
export async function requireCurrentOtp(event: NotificationEvent, request: typeof fetch = fetch): Promise<void> {
  if (event.type !== 'OtpRequested') return;
  if (!config.INTERNAL_API_KEY) throw new Error('OTP_VALIDATION_NOT_CONFIGURED');
  const response = await request(`${config.PAYMENT_SERVICE_URL}/internal/notifications/otp-current/${encodeURIComponent(event.eventId)}`, {
    headers: {'X-Internal-API-Key': config.INTERNAL_API_KEY}, signal: AbortSignal.timeout(5000),
  });
  if (!response.ok) throw new Error('OTP_VALIDATION_UNAVAILABLE');
  const result: unknown = await response.json();
  if (!result || typeof result !== 'object' || !('current' in result) || typeof result.current !== 'boolean')
    throw new Error('OTP_VALIDATION_INVALID_RESPONSE');
  if (!result.current) throw new PermanentError('OTP_SUPERSEDED_OR_CONSUMED');
}
