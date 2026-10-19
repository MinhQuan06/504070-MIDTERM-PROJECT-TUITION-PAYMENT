import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../../..');
const path=resolve(root,'.env');
let text=readFileSync(existsSync(path)?path:resolve(root,'.env.example'),'utf8');
const current=text.match(/^OUTBOX_ENCRYPTION_KEY=(.*)$/m)?.[1].trim();
if(current) {
  if(Buffer.from(current,'base64').length!==32)throw new Error('Existing OUTBOX_ENCRYPTION_KEY is invalid; no file was changed.');
  console.log('Outbox key already configured; kept unchanged.');
} else {
  const line='OUTBOX_ENCRYPTION_KEY='+randomBytes(32).toString('base64');
  text=/^OUTBOX_ENCRYPTION_KEY=.*$/m.test(text)?text.replace(/^OUTBOX_ENCRYPTION_KEY=.*$/m,line):text.trimEnd()+'\n'+line+'\n';
  writeFileSync(path,text);
  console.log('Generated Outbox key in ignored .env. Keep this key with your database backup.');
}
