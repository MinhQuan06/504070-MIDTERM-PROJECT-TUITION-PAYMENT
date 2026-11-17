import React from 'react';
import ReactDOM from 'react-dom/client';
import {ConfigProvider} from 'antd';
import viVN from 'antd/locale/vi_VN';
import 'antd/dist/reset.css';
import './styles.css';
import App from './App';
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><ConfigProvider locale={viVN} theme={{token:{colorPrimary:'#2462ed',colorText:'#142b48',colorTextSecondary:'#758399',colorBorder:'#dce4ee',borderRadius:9,controlHeight:42,fontFamily:'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',fontSize:14},components:{Button:{primaryShadow:'0 4px 10px rgba(36,98,237,.16)'},Input:{activeShadow:'0 0 0 3px rgba(36,98,237,.08)'},Modal:{borderRadiusLG:16}}}}><App/></ConfigProvider></React.StrictMode>);
