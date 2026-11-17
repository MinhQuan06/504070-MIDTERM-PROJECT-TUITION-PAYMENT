import {useState} from 'react';
import {Button,Input,Select} from 'antd';
import {RefreshCw,Search} from 'lucide-react';
import {useBank} from '../bank';
import {statusLabel} from '../ui';
import {TransactionList} from '../components/Transactions';
export default function History(){const {history,loading,reload}=useBank();const [query,setQuery]=useState(''),[status,setStatus]=useState('ALL');const visible=history.filter(tx=>(status==='ALL'||tx.status===status)&&`${tx.transactionId} ${tx.studentId}`.toLowerCase().includes(query.trim().toLowerCase()));
 return <><div className="page-heading"><div><span className="section-kicker">LỊCH SỬ THANH TOÁN</span><h1>Mỗi giao dịch, rõ ràng.</h1><p>Tra cứu và theo dõi kết quả thanh toán học phí của bạn.</p></div><Button icon={<RefreshCw size={16}/>} onClick={()=>void reload()} loading={loading}>Làm mới</Button></div><section className="panel"><div className="history-toolbar"><Input prefix={<Search size={17}/>} placeholder="Tìm mã giao dịch hoặc MSSV" aria-label="Tìm giao dịch" value={query} onChange={e=>setQuery(e.target.value)} allowClear/><Select value={status} onChange={setStatus} aria-label="Lọc trạng thái" options={[{value:'ALL',label:'Tất cả trạng thái'},...Object.entries(statusLabel).map(([value,label])=>({value,label}))]}/><span>{visible.length} giao dịch</span></div><TransactionList items={visible} loading={loading}/></section></>;
}
