import {BrowserRouter,Navigate,Route,Routes} from 'react-router-dom';
import {Button,Spin} from 'antd';
import {ShieldCheck} from 'lucide-react';
import {AuthProvider,useAuth} from './auth';
import {BankProvider} from './bank';
import Shell from './components/Shell';
import Dashboard from './pages/Dashboard';
import Payment from './pages/Payment';
import History from './pages/History';
import Login from './pages/Login';
function Content(){const {user,loading,bootError,retry,logout}=useAuth();if(loading)return <div className="boot-screen"><span className="boot-logo"><ShieldCheck size={35}/></span><h2>iBanking</h2><Spin/><p>Đang bảo vệ kết nối của bạn…</p></div>;if(bootError)return <div className="boot-screen"><ShieldCheck size={35}/><h2>Chưa thể kết nối</h2><p>{bootError}</p><div className="inline-actions"><Button type="primary" onClick={retry}>Thử lại</Button><Button onClick={logout}>Về đăng nhập</Button></div></div>;if(!user)return <Login/>;
 return <BankProvider key={user.user_id}><Routes><Route element={<Shell/>}><Route index element={<Dashboard/>}/><Route path="thanh-toan" element={<Payment/>}/><Route path="lich-su" element={<History/>}/><Route path="*" element={<Navigate to="/" replace/>}/></Route></Routes></BankProvider>;
}
export default function App(){return <BrowserRouter><AuthProvider><Content/></AuthProvider></BrowserRouter>;}
