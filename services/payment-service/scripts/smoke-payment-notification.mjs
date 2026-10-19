import assert from 'node:assert/strict';

// Run only against the local demo stack: creates a dedicated account/transactions.
const api = process.env.PAYMENT_API_URL || 'http://localhost:8080/api/v1';
const mailpit = process.env.MAILPIT_URL || 'http://localhost:8025';
const userId = Date.now();
const email = `user${userId}@tdtu.edu.vn`;
async function request(path, body, expected = 200) {
  const response = await fetch(`${api}${path}`, {
    method: body === undefined ? 'GET' : 'POST',
    headers: body === undefined ? {} : {'content-type': 'application/json'},
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(15000),
  });
  assert.equal(response.status, expected, `${path}: HTTP ${response.status}, expected ${expected}`);
  return response.json();
}
async function waitEmail(subject, excludeId) {
  for (let attempt = 0; attempt < 30; attempt++) {
    const response = await fetch(`${mailpit}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`, {signal: AbortSignal.timeout(5000)});
    assert.ok(response.ok, 'Mailpit API must be reachable');
    const list = await response.json();
    const mail = list.messages.find(m => m.Subject === subject && m.ID !== excludeId);
    if (mail) return fetch(`${mailpit}/api/v1/message/${mail.ID}`).then(r => r.json());
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error('Timed out waiting for notification email');
}
function otpFrom(mail) {
  const otp = mail.Text.match(/Mã OTP của bạn: (\d{6})/u)?.[1];
  assert.ok(otp, 'OTP must be present in the received email');
  return otp;
}
const otpSubject = 'Mã OTP xác nhận thanh toán học phí';
await request('/accounts', {userId, balance: 50000000}, 201);
const tx = await request('/transactions', {payerUserId: userId, tuitionId: 101, studentId: '52300001', amount: 15000000}, 201);
const firstMail = await waitEmail(otpSubject);
const firstOtp = otpFrom(firstMail);
console.log('PASS: Gateway -> Payment -> RabbitMQ -> Notification -> OTP email');
await request(`/transactions/${tx.transactionId}/resend-otp`, {});
const secondMail = await waitEmail(otpSubject, firstMail.ID);
const secondOtp = otpFrom(secondMail);
await request(`/transactions/${tx.transactionId}/verify-otp`, {otpCode: firstOtp}, 409);
await request(`/transactions/${tx.transactionId}/verify-otp`, {otpCode: '000000'}, 409);
console.log('PASS: resend email, old OTP and wrong OTP rejected');
const paid = await request(`/transactions/${tx.transactionId}/verify-otp`, {otpCode: secondOtp});
assert.equal(paid.status, 'SUCCESS');
const receipt = await waitEmail('Xác nhận thanh toán học phí thành công');
assert.ok(receipt.Text.includes(String(tx.transactionId)));
assert.equal((await request(`/accounts/${userId}`)).balance, 35000000);
console.log('PASS: payment success, correct debit, receipt received');
await request(`/transactions/${tx.transactionId}/verify-otp`, {otpCode: secondOtp});
assert.equal((await request(`/accounts/${userId}`)).balance, 35000000);
console.log('PASS: repeated confirmation does not debit again');
await request('/transactions', {payerUserId: userId, tuitionId: 102, studentId: '52300001', amount: 99000000}, 400);
await request('/transactions', {payerUserId: userId, tuitionId: 999, studentId: '52300001', amount: 1000}, 409);
const conflict = await request('/transactions', {payerUserId: userId, tuitionId: 888, studentId: '52300001', amount: 1000}, 201);
// Find this transaction's OTP rather than the preceding resend emails.
let conflictMail;
for (let i = 0; i < 30; i++) {
  const response = await fetch(`${mailpit}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
  const list = await response.json();
  for (const item of list.messages.filter(m => m.Subject === otpSubject)) {
    const detail = await fetch(`${mailpit}/api/v1/message/${item.ID}`).then(r => r.json());
    if (detail.Text.includes(`Giao dịch: ${conflict.transactionId}`)) { conflictMail = detail; break; }
  }
  if (conflictMail) break;
  await new Promise(r => setTimeout(r, 1000));
}
assert.ok(conflictMail, 'Conflict transaction OTP must arrive');
await request(`/transactions/${conflict.transactionId}/verify-otp`, {otpCode: otpFrom(conflictMail)}, 409);
assert.equal((await request(`/accounts/${userId}`)).balance, 35000000);
assert.equal((await request(`/transactions/${conflict.transactionId}`)).status, 'FAILED');
console.log('PASS: insufficient balance, already-paid and mock conflict/refund');
const internal = await fetch(`${api.replace(/\/api\/v1$/, '')}/internal/users/1`);
assert.equal(internal.status, 404);
console.log(`PASS: internal route blocked. Demo account=${userId}, transaction=${tx.transactionId}`);
console.log('Note: User/Student are mocks; this test does not prove concurrency or distributed recovery.');
