import {Tag} from 'antd';
import {ArrowUpRight,Check,Clock3,LoaderCircle,X} from 'lucide-react';
import type {TxStatus} from './types';
export const money=(n:number)=>new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND',maximumFractionDigits:0}).format(n);
export function date(value:string|null){if(!value)return '—';const full=/Z$|[+-]\d\d:\d\d$/.test(value)?value:`${value}+07:00`;return new Intl.DateTimeFormat('vi-VN',{dateStyle:'short',timeStyle:'short',timeZone:'Asia/Ho_Chi_Minh'}).format(new Date(full));}
export function initials(name:string){return name.trim().split(/\s+/).slice(-2).map(s=>s[0]).join('').toUpperCase();}
export const statusLabel:Record<TxStatus,string>={PENDING:'Chờ xác thực',PROCESSING:'Đang đối soát',SUCCESS:'Thành công',FAILED:'Không thành công'};
export function Status({status}:{status:TxStatus}){const icons={PENDING:<Clock3 size={12}/>,PROCESSING:<LoaderCircle size={12}/>,SUCCESS:<Check size={12}/>,FAILED:<X size={12}/>};return <Tag className={`status-tag status-${status.toLowerCase()}`} bordered={false}>{icons[status]}{statusLabel[status]}</Tag>;}
export function EmptyState({title,description,icon:Icon=ArrowUpRight}:{title:string;description:string;icon?:typeof ArrowUpRight}){return <div className="empty-state"><span><Icon size={27}/></span><h3>{title}</h3><p>{description}</p></div>;}
