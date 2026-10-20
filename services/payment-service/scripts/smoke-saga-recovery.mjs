import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../../..');
const api='http://localhost:8080/api/v1';
const student='http://localhost:5071/api';
const token=process.env.PAYMENT_TEST_TOKEN;
const adminToken=process.env.PAYMENT_TEST_ADMIN_TOKEN;
const userId=Number(process.env.PAYMENT_TEST_USER_ID);
const email=process.env.PAYMENT_TEST_EMAIL;
assert.ok(token && adminToken && userId,'Run via smoke-full-stack with PAYMENT_SAGA_TEST=true');
const internalKey=readFileSync(resolve(root,'.env'),'utf8').match(/^USER_INTERNAL_API_KEY=(.*)$/m)?.[1].trim();
assert.ok(internalKey);
function compose(args,env=process.env){
 const r=spawnSync('docker',['compose',...args],{cwd:root,env,encoding:'utf8',timeout:180000});
 assert.equal(r.status,0,`Compose command failed: ${args.join(' ')}`);
}
const pause=ms=>new Promise(r=>setTimeout(r,ms));
async function call(url,body,status=200,auth=token,method) {
 const r=await fetch(url,{method:method || (body===undefined?'GET':'POST'),headers:{'content-type':'application/json',...(url.startsWith(api)?{authorization:`Bearer ${auth}`}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(20000)});
 const text=await r.text();assert.equal(r.status,status,`${url}: ${r.status} ${text}`);
 return text?JSON.parse(text):null;
}
async function ready(){for(let i=0;i<60;i++){try{if((await fetch('http://localhost:8082/health')).ok)return;}catch{}await pause(1000);}throw Error('Payment did not become ready');}
async function otp(id){
 for(let i=0;i<40;i++){
  const list=await call(`http://localhost:8025/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
  for(const item of list.messages.filter(m=>m.Subject==='Mã OTP xác nhận thanh toán học phí')){
   const detail=await call(`http://localhost:8025/api/v1/message/${item.ID}`);
   if(detail.Text.includes(`Giao dịch: ${id}`)){const code=detail.Text.match(/Mã OTP của bạn: (\d{6})/u)?.[1];if(code)return code;}
  }await pause(1000);
 }throw Error('No OTP received');
}
let mode='pass',dropped=0;
const proxy=createServer(async(req,res)=>{
 try{
  const chunks=[];for await(const c of req)chunks.push(c);
  const isPay=req.method==='POST' && /\/pay$/.test(req.url);
  if(isPay && mode==='blackout'){res.writeHead(503);res.end();return;}
  const upstream=await fetch('http://localhost:5071'+req.url,{method:req.method,headers:{'content-type':'application/json','X-Internal-API-Key':req.headers['x-internal-api-key']||''},body:req.method==='GET'?undefined:Buffer.concat(chunks),signal:AbortSignal.timeout(10000)});
  const text=await upstream.text();
  if(isPay && mode==='drop' && upstream.ok){mode='blackout';dropped++;res.destroy();return;}
  res.writeHead(upstream.status,{'content-type':'application/json'});res.end(text);
 }catch{res.writeHead(503);res.end();}
});
await new Promise(r=>proxy.listen(0,'0.0.0.0',r));
const proxyUrl=`http://host.docker.internal:${proxy.address().port}`;
try {
 compose(['up','-d','--no-deps','--wait','payment-service'],{...process.env,STUDENT_SERVICE_URL:proxyUrl});
 await ready();
 const studentId=`R${Date.now().toString(36)}`;
 await call(`${student}/Student`,{student_ID:studentId,full_Name:'Saga recovery demo'},201);
 const fee=await call(`${student}/Tuition`,{student_ID:studentId,semester:'SAGA',amount:1000},201);
 await call(`${api}/accounts/${userId}`,undefined,404);
 await call(`${api}/accounts`,{userId,balance:50000000},201,adminToken);
 const tx=await call(`${api}/transactions`,{tuitionId:fee.tuition_ID,studentId,amount:1000},201);
 const code=await otp(tx.transactionId);
 mode='drop';
 const pending=await call(`${api}/transactions/${tx.transactionId}/verify-otp`,{otpCode:code},202);
 assert.equal(pending.status,'PROCESSING');assert.equal(dropped,1);
 assert.equal((await call(`${student}/Tuition/${fee.tuition_ID}`)).status,'PAID');
 assert.equal((await call(`${api}/accounts/${userId}`)).balance,49999000);
 console.log('PASS: Student committed PAID, response lost; Payment remains PROCESSING without refund.');
 compose(['restart','payment-service']);await ready();
 mode='pass';
 let final;
 for(let i=0;i<90;i++){final=await call(`${api}/transactions/${tx.transactionId}`);if(final.status==='SUCCESS')break;await pause(1000);}
 assert.equal(final.status,'SUCCESS');
 assert.equal((await call(`${api}/accounts/${userId}`)).balance,49999000);
 const actual=await call(`${student}/Tuition/${fee.tuition_ID}`);
 assert.equal(actual.version,1);assert.equal(actual.paid_By_Transaction_ID,tx.transactionId);
 const replay={transaction_ID:tx.transactionId,student_ID:studentId,amount:1000,expected_Version:0};
 async function pay(body,status=200,key=internalKey){const r=await fetch(`${student}/Tuition/${fee.tuition_ID}/pay`,{method:'POST',headers:{'content-type':'application/json','X-Internal-API-Key':key},body:JSON.stringify(body)});assert.equal(r.status,status);}
 await Promise.all([pay(replay),pay(replay)]);
 await pay({...replay,transaction_ID:tx.transactionId+99999},409);
 await pay({...replay,amount:2000},409);
 await pay(replay,401,'wrong');
 assert.equal((await call(`${student}/Tuition/${fee.tuition_ID}`)).version,1);
 await call(`${api}/transactions/${tx.transactionId}/verify-otp`,{otpCode:code});
 console.log('PASS: recovery after Payment restart, same transaction replay/concurrent replay is idempotent, different transaction rejected.');
 const secondFee=await call(`${student}/Tuition`,{student_ID:studentId,semester:'SAGA2',amount:1000},201);
 const second=await call(`${api}/transactions`,{tuitionId:secondFee.tuition_ID,studentId,amount:1000},201);
 const secondCode=await otp(second.transactionId);
 await call(`${student}/Tuition/${secondFee.tuition_ID}`,{student_ID:studentId,semester:'SAGA2',amount:2000},200,token,'PUT');
 await call(`${api}/transactions/${second.transactionId}/verify-otp`,{otpCode:secondCode},409);
 await call(`${api}/transactions/${second.transactionId}/verify-otp`,{otpCode:secondCode},409);
 assert.equal((await call(`${api}/transactions/${second.transactionId}`)).status,'FAILED');
 assert.equal((await call(`${api}/accounts/${userId}`)).balance,49999000);
 assert.equal((await call(`${student}/Tuition/${secondFee.tuition_ID}`)).status,'UNPAID');
 let receipt;
 for(let i=0;i<30;i++){
  const list=await call(`http://localhost:8025/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
  receipt=list.messages.filter(m=>m.Subject==='Xác nhận thanh toán học phí thành công');
  if(receipt.length)break;await pause(1000);
 }
 assert.equal(receipt.length,1);
 console.log(`PASS: stale tuition rejected, refund once, exactly one receipt observed. transaction=${tx.transactionId}, failed=${second.transactionId}`);
} finally {
 compose(['up','-d','--no-deps','--wait','payment-service']);
 proxy.closeAllConnections();await new Promise(r=>proxy.close(r));
}
