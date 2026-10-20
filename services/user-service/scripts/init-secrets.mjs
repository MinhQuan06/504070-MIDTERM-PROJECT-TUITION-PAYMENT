import {existsSync, readFileSync, writeFileSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../../..');
const path=resolve(root,'.env');
let text=readFileSync(existsSync(path)?path:resolve(root,'.env.example'),'utf8');
for (const key of ['JWT_SECRET_KEY','USER_INTERNAL_API_KEY']) {
  const value=text.match(new RegExp(`^${key}=(.*)$`,'m'))?.[1].trim();
  if (value) {
    if(value.length<32 || /CHANGE_ME|THAY_BANG|REPLACE_ME/i.test(value)) throw Error(`${key} invalid; configure a random value of at least 32 characters.`);
    continue;
  }
  const line=`${key}=${randomBytes(32).toString('hex')}`;
  text=new RegExp(`^${key}=.*$`,'m').test(text)?text.replace(new RegExp(`^${key}=.*$`,'m'),line):text.trimEnd()+'\n'+line+'\n';
}
writeFileSync(path,text);
console.log('User secrets configured in ignored root .env; existing values preserved.');
