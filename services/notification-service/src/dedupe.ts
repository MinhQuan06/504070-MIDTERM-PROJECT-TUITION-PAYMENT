import { Injectable } from '@nestjs/common';
import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { config } from './config';
@Injectable()
export class DedupeStore {
  private path(id: string) { return join(config.DEDUPE_DIR, createHash('sha256').update(id).digest('hex')); }
  async has(id: string): Promise<boolean> {
    try { await readFile(this.path(id)); return true; }
    catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return false; throw error; }
  }
  async mark(id: string): Promise<void> {
    await mkdir(config.DEDUPE_DIR, {recursive: true});
    await writeFile(this.path(id), new Date().toISOString(), {flag: 'wx'}).catch(error => {
      if (error.code !== 'EEXIST') throw error;
    });
  }
}
