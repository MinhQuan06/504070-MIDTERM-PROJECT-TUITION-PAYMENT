import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../../..');
const api=process.env.PAYMENT_API_URL || 'http://localhost:8080/api/v1';
const username=`test_${Date.now()}`;
const password=randomBytes(24).toString('hex');
const email=`${username}@example.com`;
// Creates a dedicated user, never resets an existing user's balance or password.
const fixture=`import json, jwt
from datetime import datetime, timedelta, timezone
from app.database import SessionLocal
from app.models.user import User
from app.core.security import hash_password, create_access_token
from app.config import settings
record=json.loads(${JSON.stringify(JSON.stringify({username,password,email,userId:Date.now()}))})
with SessionLocal() as db:
 user=User(user_id=record['userId'],username=record['username'],password_hash=hash_password(record['password']),full_name='Full stack demo',email=record['email'],phone='0901234567',role='student',is_active=True,failed_login_attempts=0)
 admin=User(user_id=record['userId']+1,username=record['username']+'_admin',password_hash=hash_password(record['password']),full_name='Test Admin',email='admin_'+record['email'],role='admin',is_active=True,failed_login_attempts=0)
 db.add_all([user,admin])
 db.commit()
 payload=jwt.decode(create_access_token(user.user_id),options={'verify_signature':False})
 payload['exp']=datetime.now(timezone.utc)-timedelta(minutes=5)
 expired=jwt.encode(payload,settings.JWT_SECRET_KEY,algorithm='HS256')
 print(json.dumps({'userId':user.user_id,'expired':expired}))
`;
const created=spawnSync('docker',['compose','exec','-T','user-service','python','-'],{cwd:root,input:fixture,encoding:'utf8'});
assert.equal(created.status,0,created.stderr);
const {userId,expired}=JSON.parse(created.stdout.trim());
assert.ok(Number.isSafeInteger(userId) && userId>0);
async function check(path,options={},status=200) {
 const r=await fetch(`${api}${path}`,{...options,signal:AbortSignal.timeout(15000)});
 assert.equal(r.status,status,`${path}: HTTP ${r.status}`);
 return r;
}
await check('/users/me',{},401);
const login=await check('/auth/login',{method:'POST',body:new URLSearchParams({username,password})});
const token=(await login.json()).access_token;
assert.ok(token);
const adminLogin=await check('/auth/login',{method:'POST',body:new URLSearchParams({username:username+'_admin',password})});
const adminToken=(await adminLogin.json()).access_token;
const adminHeaders={authorization:`Bearer ${adminToken}`};
const headers={authorization:`Bearer ${token}`};
const profile=await (await check('/users/me',{headers})).json();
assert.equal(profile.user_id,userId);assert.equal(profile.email,email);
await check('/admin/users',{headers},403);
await check('/users/me',{headers:{authorization:'Bearer invalid'}},401);
assert.equal((await fetch('http://localhost:8080/internal/users/'+userId)).status,404);
assert.equal((await fetch('http://localhost:8001/internal/users/'+userId)).status,401);
assert.equal((await fetch('http://localhost:8001/internal/users/'+userId,{headers:{'X-Internal-API-Key':'wrong'}})).status,401);
await check('/admin/users',{headers:adminHeaders});
await check(`/accounts/${userId}`,{},401);
await check(`/accounts/${userId}`,{headers:{authorization:`Bearer ${expired}`}},401);
const parts=token.split('.');
parts[2]=(parts[2][0]==='A'?'B':'A')+parts[2].slice(1);
await check(`/accounts/${userId}`,{headers:{authorization:`Bearer ${parts.join('.')}`}},401);
const direct=await fetch(`http://localhost:8082/api/v1/accounts/${userId}`);
assert.equal(direct.status,401,'Direct Payment port must also enforce auth');
console.log('PASS: login, JWT profile/admin, invalid signature, expired token, direct-port auth, internal route protection.');
const payment=spawnSync(process.execPath,[resolve(root,process.env.PAYMENT_SAGA_TEST==='true' ? 'services/payment-service/scripts/smoke-saga-recovery.mjs' : 'services/payment-service/scripts/smoke-payment-student.mjs')],{cwd:root,env:{...process.env,PAYMENT_TEST_USER_ID:String(userId),PAYMENT_TEST_EMAIL:email,PAYMENT_TEST_TOKEN:token,PAYMENT_TEST_ADMIN_TOKEN:adminToken},stdio:'inherit'});
assert.equal(payment.status,0,'Full stack payment must pass');
console.log(`Dedicated demo user retained: ${username}, id=${userId}. Password is random; not printed.`);
