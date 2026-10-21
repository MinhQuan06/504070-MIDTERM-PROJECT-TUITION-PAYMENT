import axios from 'axios';
import type {Account,Student,Transaction,Tuition,User} from './types';
export const TOKEN_KEY='ibanking.session';
export const api=axios.create({baseURL:'/api/v1',timeout:20000});
api.interceptors.request.use(config=>{const token=sessionStorage.getItem(TOKEN_KEY);if(token)config.headers.Authorization=`Bearer ${token}`;return config;});
api.interceptors.response.use(r=>r,error=>{if(error.response?.status===401 && !error.config?.url?.includes('/auth/login'))window.dispatchEvent(new Event('ibanking:expired'));return Promise.reject(error);});
export const getProfile=(signal?:AbortSignal)=>api.get<User>('/users/me',{signal}).then(r=>r.data);
export const getAccount=(id:number,signal?:AbortSignal)=>api.get<Account>(`/accounts/${id}`,{signal}).then(r=>r.data);
export const getHistory=(id:number,signal?:AbortSignal)=>api.get<Transaction[]>(`/transactions/user/${id}`,{signal}).then(r=>r.data);
export const getTransaction=(id:number,signal?:AbortSignal)=>api.get<Transaction>(`/transactions/${id}`,{signal}).then(r=>r.data);
export async function lookupStudent(id:string,signal?:AbortSignal){const [student,fees]=await Promise.all([api.get<Student>(`/students/${encodeURIComponent(id)}`,{signal}),api.get<Tuition[]>(`/students/${encodeURIComponent(id)}/tuition`,{signal})]);return {student:student.data,fees:fees.data};}
export const createPayment=(fee:Tuition)=>api.post<Transaction>('/transactions',{tuitionId:fee.tuition_ID,studentId:fee.student_ID,amount:fee.amount}).then(r=>r.data);
export const verifyPayment=(id:number,otpCode:string)=>api.post<Transaction>(`/transactions/${id}/verify-otp`,{otpCode}).then(r=>r.data);
export const resendOtp=(id:number)=>api.post<Transaction>(`/transactions/${id}/resend-otp`).then(r=>r.data);
export function isMissing(error:unknown){return axios.isAxiosError(error)&&error.response?.status===404;}
export function isUncertain(error:unknown){return axios.isAxiosError(error)&&(!error.response||error.response.status>=500);}
export function errorText(error:unknown):string {
 if(axios.isCancel(error))return '';
 if(!axios.isAxiosError(error))return error instanceof Error?error.message:'Có lỗi xảy ra. Vui lòng thử lại.';
 const status=error.response?.status;
 if(!status)return 'Chưa nhận được phản hồi. Kiểm tra kết nối rồi tra cứu kết quả trước khi thử lại.';
 if(status===401)return 'Phiên đăng nhập đã hết hạn hoặc thông tin đăng nhập chưa đúng.';
 if(status===404)return 'Không tìm thấy thông tin yêu cầu. Vui lòng kiểm tra mã sinh viên hoặc giao dịch.';
 if(status===403)return 'Bạn không có quyền thực hiện thao tác này.';
 if(status===429)return 'Bạn thao tác quá nhanh. Vui lòng đợi một phút rồi thử lại.';
 if(status>=500)return 'Dịch vụ đang tạm gián đoạn. Vui lòng thử tra cứu lại sau ít phút.';
 const message=error.response?.data?.message||error.response?.data?.detail;
 const translations:Record<string,string>={'Transaction already failed':'Giao dịch đã thất bại. Vui lòng kiểm tra số dư và học phí trước khi tạo lại.','Student rejected this payment. Funds have been refunded.':'Khoản học phí đã thay đổi hoặc được thanh toán trước đó. Số tiền đã được hoàn lại.','Invalid username or password.':'Tên đăng nhập hoặc mật khẩu chưa đúng.'};
 return typeof message==='string'?(translations[message]||message):status===404?'Không tìm thấy thông tin yêu cầu. Vui lòng kiểm tra lại.':'Thông tin chưa hợp lệ. Vui lòng kiểm tra và thử lại.';
}
