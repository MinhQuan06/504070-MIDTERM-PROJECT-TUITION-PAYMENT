import 'reflect-metadata';
import { EmailService } from '../src/email';
new EmailService().verify().then(() => console.log('SMTP connection OK')).catch(() => {
  console.error('SMTP connection failed; check host, port, credentials and TLS.'); process.exitCode = 1;
});
