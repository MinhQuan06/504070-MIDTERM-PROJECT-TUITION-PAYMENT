import assert from 'node:assert/strict';
const api = process.env.PAYMENT_API_URL || 'http://localhost:8080/api/v1';
const student = process.env.STUDENT_API_URL || 'http://localhost:5071/api';
const mailpit = process.env.MAILPIT_URL || 'http://localhost:8025';
const userId = Number(process.env.PAYMENT_TEST_USER_ID || Date.now());
const email = process.env.PAYMENT_TEST_EMAIL || `user${userId}@tdtu.edu.vn`;
const token = process.env.PAYMENT_TEST_TOKEN;
const adminToken = process.env.PAYMENT_TEST_ADMIN_TOKEN;
assert.ok(token && adminToken, 'Use services/user-service/scripts/smoke-full-stack.mjs to provide authenticated test users');
const studentId = `T${Date.now().toString(36)}`;
async function call(url, body, status = 200, auth = token) {
  const r = await fetch(url, {method: body === undefined ? 'GET' : 'POST', headers: {'content-type':'application/json', ...(url.startsWith(api) ? {authorization:`Bearer ${auth}`} : {})}, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15000)});
  const text = await r.text();
  assert.equal(r.status, status, `${url}: ${r.status} ${text}`);
  return text ? JSON.parse(text) : null;
}
async function mail(subject, transactionId) {
  for (let i=0;i<40;i++) {
    const list = await call(`${mailpit}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
    for (const m of list.messages.filter(m=>m.Subject===subject)) {
      const detail = await call(`${mailpit}/api/v1/message/${m.ID}`);
      if (detail.Text.includes(String(transactionId))) return detail;
    }
    await new Promise(r=>setTimeout(r,1000));
  }
  throw Error('Email did not arrive');
}
// Local demo only. Creates dedicated records and retains them for inspection.
await call(`${student}/Student`, {student_ID:studentId,full_Name:'Integration demo'},201);
const fee=await call(`${student}/Tuition`,{student_ID:studentId,semester:'TEST',amount:15000000},201);
const existingAccount=await fetch(`${api}/accounts/${userId}`, {headers:{authorization:`Bearer ${token}`},signal:AbortSignal.timeout(15000)});
assert.equal(existingAccount.status,404,'Test requires a new Payment account; refusing to overwrite balance');
await call(`${api}/accounts`,{userId,balance:50000000},403);
await call(`${api}/accounts`,{userId,balance:50000000},201,adminToken);
await call(`${api}/accounts/${userId}`,undefined,403,adminToken);
await call(`${api}/transactions/user/${userId}`,undefined,403,adminToken);
const body={tuitionId:fee.tuition_ID,studentId,amount:15000000};
await call(`${api}/transactions`,{...body,amount:1},409);
await call(`${api}/transactions`,{...body,studentId:'WRONG'},409);
await call(`${api}/transactions`,{...body,payerUserId:userId+1},403);
const tx=await call(`${api}/transactions`,body,201);
assert.equal(tx.payerUserId,userId,'Payer must come from authenticated user');
await call(`${api}/transactions/${tx.transactionId}`,undefined,403,adminToken);
await call(`${api}/transactions/${tx.transactionId}/resend-otp`,{},403,adminToken);
await call(`${api}/transactions/${tx.transactionId}/verify-otp`,{otpCode:'123456'},403,adminToken);
await call(`${api}/transactions/user/${userId}`);
const otpMail=await mail('Mã OTP xác nhận thanh toán học phí',tx.transactionId);
const otpCode=otpMail.Text.match(/Mã OTP của bạn: (\d{6})/u)?.[1];
assert.ok(otpCode);
const paid=await call(`${api}/transactions/${tx.transactionId}/verify-otp`,{otpCode});
assert.equal(paid.status,'SUCCESS');
await call(`${api}/transactions/${tx.transactionId}`);
const actual=await call(`${api}/tuitions/${fee.tuition_ID}`);
assert.equal(actual.status,'PAID');
assert.equal(actual.paid_By_Transaction_ID,tx.transactionId);
assert.equal((await call(`${api}/accounts/${userId}`)).balance,35000000);
await mail('Xác nhận thanh toán học phí thành công',tx.transactionId);
await call(`${api}/transactions/${tx.transactionId}/verify-otp`,{otpCode});
assert.equal((await call(`${api}/accounts/${userId}`)).balance,35000000);
await call(`${api}/transactions`,body,409);
console.log(`PASS: payer derived from JWT, owner checks, Admin-only balance seed, real Student lookup, amount/student validation, OTP, debit, PAID, receipt, repeat confirmation, already-paid rejection. ${process.env.PAYMENT_TEST_USER_ID ? 'Real User and Student.' : 'User mock only.'} account=${userId}, student=${studentId}, tuition=${fee.tuition_ID}, transaction=${tx.transactionId}`);
