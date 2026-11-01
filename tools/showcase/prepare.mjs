import {readFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';

export function validateBase(value){
  const url=new URL(value);
  if(url.protocol!=='http:'||!['127.0.0.1','localhost','[::1]'].includes(url.hostname)||url.username||url.password||url.pathname!=='/'||url.search||url.hash)
    throw new Error('展示准备只允许本机 HTTP 根地址。');
  return url.origin;
}
export function requireShowcase(identity){
  if(identity?.environment!=='showcase'||identity?.dataset!=='stockflow-showcase-v1')
    throw new Error('服务端未确认独立展示库，拒绝写入演示数据。');
}
function session(base){
  const cookies=new Map();let csrf='';
  async function request(method,path,body,form=false){
    const headers={Cookie:[...cookies].map(([k,v])=>k+'='+v).join('; ')};
    if(method!=='GET')headers['X-CSRF-TOKEN']=csrf;
    if(body!==undefined)headers['Content-Type']=form?'application/x-www-form-urlencoded':'application/json';
    const response=await fetch(base+path,{method,headers,body:body===undefined?undefined:form?new URLSearchParams(body):JSON.stringify(body),redirect:'error',signal:AbortSignal.timeout(15000)});
    for(const raw of response.headers.getSetCookie()){const [pair]=raw.split(';');const index=pair.indexOf('=');cookies.set(pair.slice(0,index),pair.slice(index+1));}
    const text=await response.text(),data=text?JSON.parse(text):null;
    if(!response.ok)throw new Error(method+' '+path+' 失败：'+response.status+' '+(data?.code||''));
    return data;
  }
  return {request,async login(username,password){csrf=(await request('GET','/api/auth/csrf')).token;await request('POST','/api/auth/login',{username,password},true);csrf=(await request('GET','/api/auth/csrf')).token;},async logout(){await request('POST','/api/auth/logout');}};
}
export async function prepareShowcase({base,password}){
  base=validateBase(base);if(!password)throw new Error('请通过本机私有配置提供展示口令。');
  const warehouse=session(base),buyer=session(base);
  await warehouse.login('warehouse',password);
  try{
    requireShowcase(await warehouse.request('GET','/api/showcase/identity'));
    await buyer.login('buyer',password);
    const products=JSON.parse(await readFile(new URL('./products.json',import.meta.url),'utf8')),ids=[];
    for(const product of products){
      const {quantity,safetyStock,...fields}=product;
      const page=await warehouse.request('GET','/api/skus/page?q='+encodeURIComponent(product.code)+'&size=100');
      let sku=page.items.find(item=>item.code===product.code);
      if(!sku)sku=await warehouse.request('POST','/api/skus',fields);
      ids.push(sku.id);
      const receipt=await warehouse.request('POST','/api/receipts',{requestKey:'showcase-v1-in-'+sku.code,source:'StockFlow 固定展示数据；无真实采购',items:[{skuId:sku.id,quantity}]});
      if(receipt.status==='DRAFT')await warehouse.request('POST','/api/receipts/'+receipt.id+'/confirm');
      // Reapply this fixed fixture policy so interrupted setup can recover without resetting quantities.
      await warehouse.request('PUT','/api/inventory/'+sku.id+'/policy',{safetyStock});
    }
    const orders=[];
    for(const [name,target,skuIndex]of [['completed','COMPLETED',0],['shipped','SHIPPED',1],['confirmed','CONFIRMED',3],['cancelled','CANCELLED',4],['pending','PENDING_CONFIRM',5]]){
      let order=await buyer.request('POST','/api/orders',{requestKey:'showcase-v1-'+name,items:[{skuId:ids[skuIndex],quantity:1}]});
      if(target==='CANCELLED'&&order.status==='PENDING_CONFIRM')order=await buyer.request('POST','/api/orders/'+order.id+'/cancel');
      if(['CONFIRMED','SHIPPED','COMPLETED'].includes(target)&&order.status==='PENDING_CONFIRM')order=await buyer.request('POST','/api/orders/'+order.id+'/confirm');
      if(['SHIPPED','COMPLETED'].includes(target)&&order.status==='CONFIRMED')order=await warehouse.request('POST','/api/fulfillment/orders/'+order.id+'/ship');
      if(target==='COMPLETED'&&order.status==='SHIPPED')order=await buyer.request('POST','/api/orders/'+order.id+'/complete');
      if(name==='completed'&&order.status==='COMPLETED'){
        const returned=await buyer.request('POST','/api/returns',{requestKey:'showcase-v1-return',orderId:order.id,reason:'固定展示数据：模拟整单验收回库，不涉及实物运输及退款。'});
        if(returned.status==='REQUESTED')await warehouse.request('POST','/api/returns/'+returned.id+'/confirm');
      }
      orders.push({id:order.id,status:order.status,target});
    }
    const reconciliation=await warehouse.request('GET','/api/inventory/reconciliation');
    if(reconciliation.mismatchCount!==0)throw new Error('展示库对账存在差异，请先检查。');
    return {dataset:'stockflow-showcase-v1',products:ids.length,orders,reconciliationMismatches:reconciliation.mismatchCount,notice:'真实保存的本地演示数据；待确认订单仍遵循15分钟超时规则，重复运行不重置。'};
  }finally{await Promise.allSettled([buyer.logout(),warehouse.logout()]);}
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  try{console.log(JSON.stringify(await prepareShowcase({base:process.env.STOCKFLOW_SHOWCASE_URL||'http://127.0.0.1:8096',password:process.env.STOCKFLOW_DEMO_PASSWORD}),null,2));}
  catch(error){console.error(error.message);process.exitCode=1;}
}
