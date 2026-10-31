// 8094只提供旧入口迁移，不代理登录、不保存订单、不提供第二套业务API。
import {createServer} from 'node:http';
import {pathToFileURL} from 'node:url';
export function createEntry({backend='http://127.0.0.1:8082'}={}){
  const target=new URL(backend);
  if(target.protocol!=='http:'||!['127.0.0.1','localhost','[::1]'].includes(target.hostname)||target.username||target.password||target.pathname!=='/'||target.search||target.hash||target.port==='8094')throw new Error('后台地址必须是本机HTTP根地址，不能指向8094自身。');
  const destination=new URL('/shop#products',target).href;
  return createServer((req,res)=>{
    if(!['GET','HEAD'].includes(req.method)||req.url.startsWith('/api/')){res.writeHead(410,{'Content-Type':'application/json;charset=utf-8','Cache-Control':'no-store'});return res.end(JSON.stringify({message:'请通过Java同源前台访问业务接口。'}));}
    res.writeHead(302,{Location:destination,'Cache-Control':'no-store','Referrer-Policy':'no-referrer'});res.end();
  });
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  const server=createEntry({backend:process.env.STOCKFLOW_BACKEND_URL});
  server.on('error',e=>{console.error('订购入口启动失败：'+e.code);process.exitCode=1;});
  server.listen(8094,'127.0.0.1',()=>console.log('StockFlow订购入口：http://127.0.0.1:8094 → Java /shop。请先启动Java后台。'));
}
