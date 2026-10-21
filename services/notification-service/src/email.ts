import { Injectable } from '@nestjs/common';
import nodemailer from 'nodemailer';
import { config } from './config';
import { requireCurrentOtp } from './otp-current';
import { NotificationEvent, ensureFresh } from './events';
export function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, c => ({'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'}[c]!));
}
export function renderEmail(event: NotificationEvent) {
  const when = (value: string) => new Date(value).toLocaleString('vi-VN', {timeZone: 'Asia/Ho_Chi_Minh'});
  const subject = event.type === 'OtpRequested' ? 'Mã OTP xác nhận thanh toán học phí' : 'Xác nhận thanh toán học phí thành công';
  const lines = event.type === 'OtpRequested'
    ? [`Xin chào ${event.fullName},`, `Mã OTP của bạn: ${event.otp}`, `Hết hạn: ${when(event.expiresAt)} (giờ Việt Nam).`, `Giao dịch: ${event.transactionId}`, 'Không chia sẻ mã này với bất kỳ ai.']
    : [`Xin chào ${event.fullName},`, 'Thanh toán học phí thành công.', `Mã giao dịch: ${event.transactionId}`, `MSSV: ${event.studentId}`, `Số tiền: ${new Intl.NumberFormat('vi-VN').format(event.amount)} VND`, `Thời gian: ${when(event.completedAt)} (giờ Việt Nam).`];
  return {subject, text: lines.join('\n\n'), html: `<main style="font-family:Arial,sans-serif;max-width:600px;margin:auto;color:#18324b"><h2>iBanking · TDTU</h2><h3>${subject}</h3>${lines.map(l => `<p>${escapeHtml(l)}</p>`).join('')}<hr><small>Email tự động từ phân hệ thanh toán học phí.</small></main>`};
}
@Injectable()
export class EmailService {
  private readonly transport = nodemailer.createTransport({
    host: config.SMTP_HOST, port: config.SMTP_PORT, secure: config.SMTP_SECURE,
    requireTLS: config.SMTP_REQUIRE_TLS,
    auth: config.SMTP_USER ? {user: config.SMTP_USER, pass: config.SMTP_PASSWORD} : undefined,
    connectionTimeout: 10000, greetingTimeout: 10000, socketTimeout: 15000,
  });
  async verify(): Promise<void> { await this.transport.verify(); }
  async send(event: NotificationEvent): Promise<void> {
    await requireCurrentOtp(event);
    ensureFresh(event);
    await this.transport.sendMail({from: config.MAIL_FROM, to: event.email,
      messageId: `<${Buffer.from(event.eventId).toString('hex')}@ibanking.local>`, ...renderEmail(event)});
  }
}
