// 仅本地协议演练。Stripe HTTP 被受控测试响应替代，未产生平台交易或真实资金流。
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,readFile,writeFile,copyFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {randomBytes,createHash,createHmac} from 'node:crypto';
import {createStoreServer} from '../server/store.mjs';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const tmp=await mkdtemp(join(tmpdir(),'xuwu-real-goods-'));
const products=JSON.parse(await readFile(join(root,'public/catalog.json'),'utf8')).products.filter(p=>p.fulfillmentReady);
await mkdir(join(tmp,'public'),{recursive:true});
await writeFile(join(tmp,'public/catalog.json'),JSON.stringify({store:{name:'本地真实文件演练'},products}));
for(const p of products){await mkdir(join(tmp,'private/products',p.id),{recursive:true});await copyFile(join(root,'private/products',p.id,'download.zip'),join(tmp,'private/products',p.id,'download.zip'));}
const secret='whsec_'+randomBytes(24).toString('hex'),sessions=new Map();
let serial=0;
const fakeStripe=async(url,init={})=>{
  const u=new URL(url);assert.equal(u.origin,'https://api.stripe.com');
  if(init.method==='POST'){
    const body=new URLSearchParams(init.body),items=[];
    for(let i=0;body.has(`line_items[${i}][quantity]`);i++){
      const pre=`line_items[${i}]`,id=body.get(pre+'[price_data][product_data][metadata][store_product_id]');
      const amount=Number(body.get(pre+'[price_data][unit_amount]')),currency=body.get(pre+'[price_data][currency]');
      items.push({quantity:1,currency,amount_total:amount,amount_subtotal:amount,price:{currency,unit_amount:amount,livemode:false,product:{metadata:{store_product_id:id}}}});
    }
    const id='cs_test_controlled_'+(++serial),currency=items[0].currency;
    const session={id,mode:'payment',livemode:false,status:'open',payment_status:'unpaid',currency,amount_total:items.reduce((s,x)=>s+x.amount_total,0),metadata:{store_order_id:body.get('metadata[store_order_id]'),store_session_id:body.get('metadata[store_session_id]')},url:'https://checkout.stripe.com/c/pay/'+id};
    sessions.set(id,{session,items});return Response.json(session);
  }
  const data=sessions.get(u.pathname.split('/')[4]);assert.ok(data);
  return Response.json(u.pathname.endsWith('/line_items')?{data:data.items,has_more:false}:data.session);
};
const env={PAYMENTS_MODE:'test',BASE_URL:'http://127.0.0.1:8094',STRIPE_SECRET_KEY:'sk_test_controlled_local_only',STRIPE_WEBHOOK_SECRET:secret};
let app=createStoreServer({rootDir:tmp,env,stripeFetch:fakeStripe});
await new Promise(r=>app.server.listen(0,'127.0.0.1',r));
let base='http://127.0.0.1:'+app.server.address().port;
const sessionRes=await fetch(base+'/api/session'),{csrfToken}=await sessionRes.json(),cookie=sessionRes.headers.get('set-cookie').split(';')[0];
const request=(path,body)=>fetch(base+path,{method:body?'POST':'GET',headers:{Cookie:cookie,...(body?{Origin:env.BASE_URL,'X-CSRF-Token':csrfToken,'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{})});
const sha=b=>createHash('sha256').update(b).digest('hex');
const checks=[];
async function event(order,type,paid=false){
  const d=[...sessions.values()].find(x=>x.session.metadata.store_order_id===order.id);
  if(paid){d.session.status='complete';d.session.payment_status='paid';}
  const raw=JSON.stringify({id:'evt_local_'+order.id+'_'+type.replaceAll('.','_'),type,livemode:false,data:{object:{id:d.session.id}}}),t=Math.floor(Date.now()/1000);
  const signature=createHmac('sha256',secret).update(t+'.'+raw).digest('hex');
  const response=await fetch(base+'/api/webhooks/stripe',{method:'POST',headers:{'Stripe-Signature':`t=${t},v1=${signature}`},body:raw});assert.equal(response.status,200);return response;
}
try{
  const res=await request('/api/checkout',{items:products.map(p=>p.id),requestKey:'actual-goods-local-0001'});assert.equal(res.status,201);
  const {order}=await res.json();
  assert.equal((await request(`/api/download/c-icons?order=${order.id}`)).status,403);checks.push('真实ZIP未付款拒绝403');
  assert.equal((await request(`/api/order?id=${order.id}&paid=true`)).status,200);
  assert.equal((await (await request(`/api/order?id=${order.id}`)).json()).order.status,'PENDING');checks.push('伪造跳转参数不能标记付款');
  await event(order,'checkout.session.completed');assert.equal((await (await request(`/api/order?id=${order.id}`)).json()).order.canDownload,false);checks.push('异步未到账不开放下载');
  await event(order,'checkout.session.async_payment_succeeded',true);
  await event(order,'checkout.session.async_payment_succeeded',true);checks.push('重复签名通知幂等');
  const downloads=[];
  for(const p of products){const response=await request(`/api/download/${p.id}?order=${order.id}`);assert.equal(response.status,200);assert.equal(response.headers.get('cache-control'),'no-store');const bytes=Buffer.from(await response.arrayBuffer()),source=await readFile(join(root,'private/products',p.id,'download.zip'));assert.deepEqual(bytes,source);downloads.push({id:p.id,bytes:bytes.length,sha256:sha(bytes)});}
  checks.push('已核验订单收到正确的两个真实商品ZIP，逐字节一致');
  await app.close();app=createStoreServer({rootDir:tmp,env,stripeFetch:fakeStripe});await new Promise(r=>app.server.listen(0,'127.0.0.1',r));base='http://127.0.0.1:'+app.server.address().port;
  assert.equal((await (await request(`/api/order?id=${order.id}`)).json()).order.status,'PAID');checks.push('服务重启后会话和订单授权保留');
  const failedRes=await request('/api/checkout',{items:[products[0].id],requestKey:'actual-goods-failed-0001'}),{order:failed}=await failedRes.json();await event(failed,'checkout.session.async_payment_failed');assert.equal((await request(`/api/download/${products[0].id}?order=${failed.id}`)).status,403);checks.push('失败订单不交付');
  const report={date:'2026-10-30',type:'本地受控HTTP与签名回调演练',externalPaymentPlatformContacted:false,platformSandboxPassed:false,realFundsMoved:false,checks,downloads,limitations:['外部支付API使用受控测试响应，不能替代Stripe沙箱交易','没有浏览器支付、移动布局或Canva跨账号复制验收','所有测试订单在独立临时目录，开发商城不生成假订单']};
  await mkdir(join(root,'docs/evidence'),{recursive:true});await writeFile(join(root,'docs/evidence/local-delivery.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
}finally{await app.close();await rm(tmp,{recursive:true,force:true});}
