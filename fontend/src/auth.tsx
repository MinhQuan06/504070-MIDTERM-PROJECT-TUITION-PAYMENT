import {createContext,useContext,useEffect,useState} from 'react';
import type {ReactNode} from 'react';
import {api,errorText,getProfile,TOKEN_KEY} from './api';
import type {User} from './types';
interface AuthValue {user:User|null;loading:boolean;bootError:string;login:(username:string,password:string)=>Promise<void>;logout:()=>void;retry:()=>void}
const AuthContext=createContext<AuthValue>(null!);
export function AuthProvider({children}:{children:ReactNode}){
 const [user,setUser]=useState<User|null>(null),[loading,setLoading]=useState(true),[bootError,setBootError]=useState(''),[attempt,setAttempt]=useState(0);
 const logout=()=>{sessionStorage.removeItem(TOKEN_KEY);setUser(null);setBootError('');};
 useEffect(()=>{window.addEventListener('ibanking:expired',logout);return()=>window.removeEventListener('ibanking:expired',logout);},[]);
 useEffect(()=>{const controller=new AbortController();setLoading(true);setBootError('');if(!sessionStorage.getItem(TOKEN_KEY)){setLoading(false);return;}
 getProfile(controller.signal).then(profile=>{if(!Number.isSafeInteger(profile.user_id))throw Error('Mã tài khoản chưa được hỗ trợ trên giao diện.');setUser(profile);}).catch(e=>{if(!controller.signal.aborted&&sessionStorage.getItem(TOKEN_KEY))setBootError(errorText(e));}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});return()=>controller.abort();},[attempt]);
 async function login(username:string,password:string){
  const {data}=await api.post<{access_token:string}>('/auth/login',new URLSearchParams({username,password}),{headers:{'Content-Type':'application/x-www-form-urlencoded'}});
  sessionStorage.setItem(TOKEN_KEY,data.access_token);
  try{const profile=await getProfile();if(!Number.isSafeInteger(profile.user_id))throw Error('Mã tài khoản chưa được hỗ trợ trên giao diện.');setUser(profile);setBootError('');}catch(e){sessionStorage.removeItem(TOKEN_KEY);throw e;}
 }
 return <AuthContext.Provider value={{user,loading,bootError,login,logout,retry:()=>setAttempt(n=>n+1)}}>{children}</AuthContext.Provider>;
}
export const useAuth=()=>useContext(AuthContext);
