export interface User {user_id:number;username:string;full_name:string;phone:string|null;email:string;role:string}
export interface Account {userId:number;balance:number;version:number}
export interface Student {student_ID:string;full_Name:string}
export interface Tuition {tuition_ID:number;student_ID:string;semester:string;amount:number;status:'UNPAID'|'PAID';version:number;paid_By_Transaction_ID:number|null}
export type TxStatus='PENDING'|'PROCESSING'|'SUCCESS'|'FAILED';
export interface Transaction {transactionId:number;payerUserId:number;payerEmail:string|null;tuitionId:number;studentId:string;amount:number;status:TxStatus;createdAt:string;completedAt:string|null;message:string|null}
