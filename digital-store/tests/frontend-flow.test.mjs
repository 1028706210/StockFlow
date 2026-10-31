// 前端状态与请求边界测试，不是浏览器视觉或交互验收。
import test from 'node:test';import assert from 'node:assert/strict';import vm from 'node:vm';import {readFile} from 'node:fs/promises';
import {restoreCart,cartSummary,filterProducts} from '../public/shop-model.mjs';
const data=JSON.parse(await readFile(new URL('../public/catalog.json',import.meta.url),'utf8'));
const code=(await readFile(new URL('../public/app.js',import.meta.url),'utf8')).replace(/^import .*;\r?\n/gm,'');
async function page({hash='#/',storage=new Map(),request,mode='disabled'}={}){
  const els=new Map(),events={};
  const element=()=>({innerHTML:'',textContent:'',hidden:false,disabled:false,value:'',listeners:{},setAttribute(){},focus(){},append(){},replaceChildren(){},addEventListener(k,fn){this.listeners[k]=fn;}});
  const get=k=>{if(!els.has(k))els.set(k,element());return els.get(k);};
  const location={hash},requests=[];
  const context={document:{querySelector:get,querySelectorAll:()=>[],createElement:element,title:''},location,window:{addEventListener(k,fn){events[k]=fn},scrollTo(){}},localStorage:{getItem:k=>storage.get(k),setItem:(k,v)=>storage.set(k,v)},Intl,Date,Map,Set,Number,String,Error,JSON,URLSearchParams,crypto:{randomUUID:()=> 'consistent-request-key'},setTimeout:()=>1,clearTimeout(){},restoreCart,cartSummary,filterProducts,fetch:async(path,options={})=>{requests.push({path,options});if(request)return request(path,options);return Response.json(path==='/api/session'?{csrfToken:'csrf-test'}:{...data,payment:{mode,checkoutAvailable:mode==='test',message:'真实收款未开通'}});}};
  await vm.runInNewContext(`(async()=>{${code}\nglobalThis.probe={render,route};})()`,context);
  const click=async attributes=>get('#main').listeners.click({target:{closest:()=>({dataset:{},...attributes})}});
  return {get,context,storage,requests,click,events,navigate(hash){location.hash=hash;context.probe.render();}};
}
test('目录分类与搜索真实生成对应商品，不显示未匹配卡片',async()=>{
  const p=await page({hash:'#/products?category=svg'});let html=p.get('#main').innerHTML;
  assert.match(html,/href="#\/product\/c-icons"/);assert.match(html,/href="#\/product\/d-decor"/);assert.doesNotMatch(html,/href="#\/product\/a-social"/);
  p.get('#search').value='不存在的素材';await p.get('#main').listeners.submit({preventDefault(){},target:{id:'search-form'}});
  assert.match(p.get('#main').innerHTML,/没有找到匹配的素材/);
});
test('购物袋选择刷新后保留，移除后恢复空状态',async()=>{
  const p=await page();await p.click({dataset:{add:'c-icons'}});assert.equal(p.get('#cart-count').textContent,'1');
  const reloaded=await page({hash:'#/cart',storage:p.storage});assert.match(reloaded.get('#main').innerHTML,/日常商店/);assert.match(reloaded.get('#main').innerHTML,/id="checkout"[^>]*disabled/);
  await reloaded.click({dataset:{remove:'c-icons'}});assert.match(reloaded.get('#main').innerHTML,/购物袋还是空的/);assert.equal(reloaded.get('#cart-count').textContent,'0');
});
test('Canva商品详情明确复制链接待核验，购物袋阻止结算',async()=>{
  const p=await page({hash:'#/product/a-social'});assert.match(p.get('#main').innerHTML,/买家复制链接尚待核验/);
  await p.click({dataset:{add:'a-social'}});p.navigate('#/cart');assert.match(p.get('#main').innerHTML,/id="checkout"[^>]*disabled/);assert.match(p.get('#main').innerHTML,/交付未核验/);
});
test('测试结算只传商品ID，网络失败保留购物袋和请求键',async()=>{
  const storage=new Map([['xuwu-cart','["c-icons"]']]);
  const p=await page({hash:'#/cart',storage,request:async(path)=>path==='/api/session'?Response.json({csrfToken:'csrf-test'}):path==='/api/catalog'?Response.json({...data,payment:{mode:'test',checkoutAvailable:true}}):Response.json({message:'平台暂不可用'},{status:502})});
  await p.click({id:'checkout'});await p.click({id:'checkout'});
  const calls=p.requests.filter(r=>r.path==='/api/checkout');assert.equal(calls.length,2);
  const body=JSON.parse(calls[0].options.body);assert.deepEqual(body,{items:['c-icons'],requestKey:'consistent-request-key'});assert.equal(calls[1].options.body,calls[0].options.body);
  assert.equal(p.get('#checkout-error').textContent,'平台暂不可用');assert.equal(storage.get('xuwu-cart'),'["c-icons"]');
});
test('订单旧响应不会覆盖用户已切换的商品页',async()=>{
  let resolveOrder;
  const p=await page({hash:'#/order',request:async(path)=>path==='/api/session'?Response.json({csrfToken:'csrf-test'}):path==='/api/catalog'?Response.json({...data,payment:{mode:'disabled',checkoutAvailable:false}}):new Promise(r=>{resolveOrder=r;})});
  p.navigate('#/products?category=svg');const original=p.get('#main').innerHTML;
  resolveOrder(Response.json({orders:[]}));await new Promise(r=>setImmediate(r));assert.equal(p.get('#main').innerHTML,original);
});
test('结算网络结果不确定时刷新页面仍保留原请求键',async()=>{
  const storage=new Map([['xuwu-cart','["c-icons"]'],['xuwu-checkout',JSON.stringify({items:['c-icons'],requestKey:'network-retry-key-456'})]]);
  const p=await page({hash:'#/cart',storage,request:async(path)=>path==='/api/session'?Response.json({csrfToken:'csrf-test'}):path==='/api/catalog'?Response.json({...data,payment:{mode:'test',checkoutAvailable:true}}):Response.json({message:'平台暂不可用'},{status:502})});
  await p.click({id:'checkout'});
  assert.equal(JSON.parse(p.requests.find(r=>r.path==='/api/checkout').options.body).requestKey,'network-retry-key-456');
});
