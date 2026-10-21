import {createContext,useContext,useEffect,useRef,useState} from 'react';
import type {ReactNode} from 'react';
import {errorText,getAccount,getHistory,getTransaction,isMissing} from './api';
import {useAuth} from './auth';
import type {Account,Transaction} from './types';
interface BankValue {account:Account|null;history:Transaction[];loading:boolean;error:string;reload:()=>Promise<void>;active:Transaction|null;setActive:(tx:Transaction|null)=>void;restoring:boolean;restoreError:string;retryRestore:()=>void}
const BankContext=createContext<BankValue>(null!);
export function BankProvider({children}:{children:ReactNode}){
 const {user}=useAuth();const id=user!.user_id;
 const [account,setAccount]=useState<Account|null>(null),[history,setHistory]=useState<Transaction[]>([]),[loading,setLoading]=useState(true),[error,setError]=useState('');
 const [active,setActiveState]=useState<Transaction|null>(null),[restoring,setRestoring]=useState(true),[restoreError,setRestoreError]=useState(''),[restoreAttempt,setRestoreAttempt]=useState(0);
 const alive=useRef(true);useEffect(()=>{alive.current=true;return()=>{alive.current=false;};},[]);
 const key=`ibanking.transaction.${id}`;
 function setActive(tx:Transaction|null){setActiveState(tx);if(tx)sessionStorage.setItem(key,String(tx.transactionId));else sessionStorage.removeItem(key);}
 async function reload(){setLoading(true);const [a,h]=await Promise.allSettled([getAccount(id),getHistory(id)]);if(!alive.current)return;
  const errors:string[]=[];if(a.status==='fulfilled')setAccount(a.value);else{setAccount(null);errors.push(isMissing(a.reason)?'Tài khoản của bạn chưa được cấp số dư. Vui lòng liên hệ quản trị viên.':errorText(a.reason));}
  if(h.status==='fulfilled')setHistory(h.value);else errors.push(errorText(h.reason));setError(errors.join(' '));setLoading(false);
 }
 useEffect(()=>{void reload();},[id]);
 useEffect(()=>{const controller=new AbortController();const saved=sessionStorage.getItem(key);setRestoreError('');if(!saved){setRestoring(false);return;}
 setRestoring(true);const txId=Number(saved);if(!Number.isSafeInteger(txId)||txId<=0){sessionStorage.removeItem(key);setRestoring(false);return;}
 getTransaction(txId,controller.signal).then(tx=>{setActiveState(tx);}).catch(e=>{if(controller.signal.aborted)return;if(isMissing(e))sessionStorage.removeItem(key);else setRestoreError(errorText(e));}).finally(()=>{if(!controller.signal.aborted)setRestoring(false);});return()=>controller.abort();},[id,restoreAttempt]);
 useEffect(()=>{if(active?.status!=='PROCESSING')return;let stop=false;let timer:ReturnType<typeof setTimeout>;const controller=new AbortController();
 const poll=async()=>{try{const tx=await getTransaction(active.transactionId,controller.signal);if(stop)return;setActive(tx);setRestoreError('');if(tx.status!=='PROCESSING'){void reload();return;}}catch(e){if(stop)return;setRestoreError(errorText(e));}if(!stop)timer=setTimeout(poll,3000);};timer=setTimeout(poll,2000);return()=>{stop=true;clearTimeout(timer);controller.abort();};},[active?.transactionId,active?.status]);
 return <BankContext.Provider value={{account,history,loading,error,reload,active,setActive,restoring,restoreError,retryRestore:()=>setRestoreAttempt(n=>n+1)}}>{children}</BankContext.Provider>;
}
export const useBank=()=>useContext(BankContext);
