import {test} from 'node:test';
import assert from 'node:assert/strict';
import {config} from '../src/config';
import {requireCurrentOtp} from '../src/otp-current';
import {PermanentError, type NotificationEvent} from '../src/events';

config.INTERNAL_API_KEY = 'unit-test-internal-key';
const event: NotificationEvent = {eventId:'old-challenge', transactionId:'1', email:'test@example.com',
  fullName:'Test', type:'OtpRequested', otp:'123456', expiresAt:new Date(Date.now()+300000).toISOString()};
const response = (body:unknown, status=200) => (async()=>new Response(JSON.stringify(body),{status})) as typeof fetch;

test('retry discards an OTP that Payment has superseded', async()=>{
  await assert.rejects(()=>requireCurrentOtp(event,response({current:false})),PermanentError);
  await requireCurrentOtp({...event,eventId:'new-challenge'},response({current:true}));
  await assert.rejects(()=>requireCurrentOtp(event,response({current:false})),PermanentError);
});
test('lookup outage and malformed response are retryable, not permission to send',async()=>{
  for(const request of [response({},503),response({}),response({current:'true'})])
    await assert.rejects(()=>requireCurrentOtp(event,request),error=>!(error instanceof PermanentError));
});
test('receipt delivery does not depend on the OTP lookup',async()=>{
  const receipt:NotificationEvent = {eventId:'receipt',transactionId:'1',email:'test@example.com',fullName:'Test',
    type:'PaymentSucceeded',studentId:'SV001',amount:1000,completedAt:new Date().toISOString()};
  await requireCurrentOtp(receipt,async()=>{throw Error('must not call Payment');});
});
