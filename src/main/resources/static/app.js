'use strict';
let authorization = '';
const statusElement=document.querySelector('#result-status');
const resultElement=document.querySelector('#result');
function show(status,data,error=false){
  statusElement.textContent=status;statusElement.dataset.error=String(error);
  resultElement.textContent=JSON.stringify(data,null,2);
}
async function api(method,path,body){
  if(!authorization)throw new Error('请先验证演示身份');
  const headers={Authorization:authorization};
  if(body!==undefined)headers['Content-Type']='application/json';
  const response=await fetch(path,{method,headers,credentials:'omit',body:body===undefined?undefined:JSON.stringify(body)});
  const data=await response.json();
  show(method+' '+path+' → '+response.status,data,!response.ok);
  if(!response.ok)throw new Error(data.message||'请求失败');
  return data;
}
function action(fn){return async event=>{
  event.preventDefault();
  try{await fn(event);}catch(error){statusElement.textContent=error.message;statusElement.dataset.error='true';}
};}
document.querySelector('#login').addEventListener('submit',action(async event=>{
  const values=new FormData(event.target);
  authorization='Basic '+btoa(values.get('username')+':'+values.get('password'));
  try{const me=await api('GET','/api/auth/me');document.querySelector('#identity').textContent=me.username+' · '+me.roles.join(', ');}
  catch(error){authorization='';document.querySelector('#identity').textContent='身份验证失败';throw error;}
}));
document.querySelector('#sku').addEventListener('submit',action(async event=>{
  const v=new FormData(event.target);
  const sku=await api('POST','/api/skus',{code:v.get('code'),name:v.get('name'),price:Number(v.get('price'))});
  document.querySelector('#receipt input[name=skuId]').value=sku.id;
  document.querySelector('#order textarea').value=JSON.stringify([{skuId:sku.id,quantity:2}]);
}));
document.querySelector('#receipt').addEventListener('submit',action(async event=>{
  const v=new FormData(event.target);
  await api('POST','/api/inventory/'+Number(v.get('skuId'))+'/receipts',{quantity:Number(v.get('quantity')),operationKey:v.get('operationKey')});
}));
document.querySelector('#order').addEventListener('submit',action(async event=>{
  const v=new FormData(event.target);
  const order=await api('POST','/api/orders',{requestKey:v.get('requestKey'),items:JSON.parse(v.get('items'))});
  document.querySelector('#detail input').value=order.id;
}));
document.querySelector('#detail').addEventListener('submit',action(async event=>{
  await api('GET','/api/orders/'+Number(new FormData(event.target).get('id')));
}));
for(const [id,path] of [['load-skus','/api/skus'],['load-stock','/api/inventory'],['load-orders','/api/orders']])
  document.getElementById(id).addEventListener('click',action(()=>api('GET',path)));
document.querySelector('#new-key').addEventListener('click',()=>document.querySelector('#order input').value=crypto.randomUUID());
document.querySelector('#order input').value=crypto.randomUUID();
document.querySelector('#receipt input[name=operationKey]').value=crypto.randomUUID();
