import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../../..');
const api='http://localhost:8080/api/v1', student='http://localhost:5071/api', mailpit='http://localhost:8025';
const tag=Date.now(), password=randomBytes(24).toString('hex');
const records=Array.from({length:7},(_,i)=>({id:tag+i,username:`cc_${tag}_${i}`,email:`cc_${tag}_${i}@example.com`,role:i===6?'admin':'student'}));
const fixture=`import json
from app.database import SessionLocal
from app.models.user import User
from app.core.security import hash_password
records=json.loads(${JSON.stringify(JSON.stringify(records))})
password=${JSON.stringify(password)}
with SessionLocal() as db:
 for r in records:
  db.add(User(user_id=r['id'],username=r['username'],email=r['email'],full_name='Concurrency demo',password_hash=hash_password(password),role=r['role'],is_active=True,failed_login_attempts=0))
 db.commit()
`;
const seeded=spawnSync('docker',['compose','exec','-T','user-service','python','-'],{cwd:root,input:fixture,encoding:'utf8'});
assert.equal(seeded.status,0,seeded.stderr);
const pause=ms=>new Promise(r=>setTimeout(r,ms));
async function request(url,body,token,status=200){
 const r=await fetch(url,{method:body===undefined?'GET':'POST',headers:{'content-type':'application/json',...(token?{authorization:`Bearer ${token}`}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)});
 const text=await r.text(),data=text?JSON.parse(text):null;
 if(status!==null)assert.equal(r.status,status,`${url}: ${r.status} ${text}`);
 return {status:r.status,data};
}
for(const user of records){
 const r=await fetch(`${api}/auth/login`,{method:'POST',body:new URLSearchParams({username:user.username,password}),signal:AbortSignal.timeout(15000)});
 assert.equal(r.status,200,'Demo login must succeed (User limits login attempts per minute)');
 user.token=(await r.json()).access_token;
}
const admin=records[6];
async function get(url,user){return (await request(url,undefined,user?.token)).data;}
async function otp(tx,user){
 for(let i=0;i<40;i++){
  const list=await get(`${mailpit}/api/v1/search?query=${encodeURIComponent(`to:${user.email}`)}`);
  for(const m of list.messages.filter(m=>m.Subject==='Mã OTP xác nhận thanh toán học phí')){
   const detail=await get(`${mailpit}/api/v1/message/${m.ID}`);
   if(detail.Text.includes(`Giao dịch: ${tx.transactionId}`)){
    const code=detail.Text.match(/Mã OTP của bạn: (\d{6})/u)?.[1];if(code)return code;
   }
  }await pause(1000);
 }throw Error('OTP did not arrive');
}
async function settle(tx,user){
 for(let i=0;i<45;i++){const current=await get(`${api}/transactions/${tx.transactionId}`,user);if(current.status!=='PROCESSING')return current;await pause(1000);}
 throw Error('Transaction did not settle');
}
const all=[];
for(let round=0;round<3;round++){
 const owner=records[round*2], other=records[round*2+1];
 for(const user of [owner,other]){
  await request(`${api}/accounts/${user.id}`,undefined,user.token,404);
  await request(`${api}/accounts`,{userId:user.id,balance:1500},admin.token,201);
 }
 const studentId=`C${tag.toString(36)}${round}`;
 await request(`${student}/Student`,{student_ID:studentId,full_Name:'Concurrency demo'},undefined,201);
 const fees=[];
 for(let i=0;i<3;i++)fees.push((await request(`${student}/Tuition`,{student_ID:studentId,semester:`CC${i}`,amount:i===2?400:1000},undefined,201)).data);
 const started=await Promise.all(fees.slice(0,2).map(fee=>request(`${api}/transactions`,{tuitionId:fee.tuition_ID,studentId,amount:1000},owner.token,201).then(r=>r.data)));
 const codes=await Promise.all(started.map(tx=>otp(tx,owner)));
 const confirmations=await Promise.all(started.map((tx,i)=>request(`${api}/transactions/${tx.transactionId}/verify-otp`,{otpCode:codes[i]},owner.token,null)));
 assert.ok(confirmations.every(r=>[200,202,400].includes(r.status)),JSON.stringify(confirmations.map(r=>r.status)));
 const states=await Promise.all(started.map(tx=>settle(tx,owner)));
 assert.equal(states.filter(t=>t.status==='SUCCESS').length,1);
 assert.equal(states.filter(t=>t.status==='PENDING').length,1);
 assert.equal((await get(`${api}/accounts/${owner.id}`,owner)).balance,500);
 for(let i=0;i<2;i++){
  const actual=await get(`${api}/tuitions/${fees[i].tuition_ID}`,owner);
  assert.equal(actual.status,states[i].status==='SUCCESS'?'PAID':'UNPAID');
  if(actual.status==='PAID')assert.equal(actual.paid_By_Transaction_ID,started[i].transactionId);
 }
 // Repeated confirmation of the winner must not spend again.
 const winner=states.findIndex(t=>t.status==='SUCCESS');
 await Promise.all([0,1].map(()=>request(`${api}/transactions/${started[winner].transactionId}/verify-otp`,{otpCode:codes[winner]},owner.token)));
 assert.equal((await get(`${api}/accounts/${owner.id}`,owner)).balance,500);
 console.log(`PASS round ${round+1}: same account, two payments of 1000 with balance 1500 -> one SUCCESS, one PENDING, balance 500.`);
 const shared=await Promise.all([owner,other].map(user=>request(`${api}/transactions`,{tuitionId:fees[2].tuition_ID,studentId,amount:400},user.token,201).then(r=>r.data)));
 const sharedCodes=await Promise.all(shared.map((tx,i)=>otp(tx,[owner,other][i])));
 const sharedResponses=await Promise.all(shared.map((tx,i)=>request(`${api}/transactions/${tx.transactionId}/verify-otp`,{otpCode:sharedCodes[i]},[owner,other][i].token,null)));
 assert.ok(sharedResponses.every(r=>[200,202,409].includes(r.status)),JSON.stringify(sharedResponses.map(r=>r.status)));
 const sharedStates=await Promise.all(shared.map((tx,i)=>settle(tx,[owner,other][i])));
 assert.equal(sharedStates.filter(t=>t.status==='SUCCESS').length,1);
 assert.equal(sharedStates.filter(t=>t.status==='FAILED').length,1);
 const sharedWinner=sharedStates.findIndex(t=>t.status==='SUCCESS');
 const actual=await get(`${api}/tuitions/${fees[2].tuition_ID}`,owner);
 assert.equal(actual.status,'PAID');assert.equal(actual.version,1);
 assert.equal(actual.paid_By_Transaction_ID,shared[sharedWinner].transactionId);
 assert.equal((await get(`${api}/accounts/${owner.id}`,owner)).balance,sharedWinner===0?100:500);
 assert.equal((await get(`${api}/accounts/${other.id}`,other)).balance,sharedWinner===1?1100:1500);
 const historyOwner=await get(`${api}/transactions/user/${owner.id}`,owner), historyOther=await get(`${api}/transactions/user/${other.id}`,other);
 assert.equal(historyOwner.length,3);assert.equal(historyOther.length,1);
 for(const tx of [...states,sharedStates[0]])assert.equal(historyOwner.find(t=>t.transactionId===tx.transactionId)?.status,tx.status);
 assert.equal(historyOther[0].status,sharedStates[1].status);
 console.log(`PASS round ${round+1}: two accounts pay same fee -> one SUCCESS, one FAILED/refunded, tuition version 1, balances/history consistent.`);
 all.push({owner,other,successful:[started[winner].transactionId,shared[sharedWinner].transactionId]});
}
// Wait for both successful receipts in each round, and ensure pending/failed transactions have none.
for(let attempt=0;attempt<40;attempt++){
 let complete=true;
 for(const round of all){
  const ids=[];
  for(const user of [round.owner,round.other]){
   const list=await get(`${mailpit}/api/v1/search?query=${encodeURIComponent(`to:${user.email}`)}`);
   for(const m of list.messages.filter(m=>m.Subject==='Xác nhận thanh toán học phí thành công')){
    const detail=await get(`${mailpit}/api/v1/message/${m.ID}`);
    const id=detail.Text.match(/Mã giao dịch:\s*(\d+)/u)?.[1];assert.ok(id,'Receipt must identify transaction');ids.push(Number(id));
   }
  }
  assert.ok(ids.length<=2,'No duplicate or failed-payment receipt expected');
  if(ids.length<2)complete=false;
  else assert.deepEqual(ids.sort((a,b)=>a-b),round.successful.slice().sort((a,b)=>a-b));
 }
 if(complete){console.log('PASS: 3 rounds, 12 transactions, 6 successful receipts observed; no overspend or duplicate tuition payment. Demo records retained.');process.exit(0);}
 await pause(1000);
}
throw Error('Receipts did not arrive');
