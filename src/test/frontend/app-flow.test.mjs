import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {parseQuantity,priceToCents,cartPreview,paginateRows,stockState,orderActions,orderTimeline} from '../../main/resources/static/ui-model.mjs';

import {parseHash,queryHash,queryParams,validateRange,snapshotLabel} from '../../main/resources/static/query-state.mjs';
class Element {
  constructor(id=''){this.id=id;this.dataset={};this.children=[];this.listeners={};this.fields=new Map();this.value='';this.open=false;this.disabled=false;this.hidden=false;this.ownText='';this.classList={add(){},remove(){},toggle(){},contains(){return false;}};}
  set textContent(text){this.ownText=String(text);this.children=[];}
  get textContent(){return this.ownText+this.children.map(c=>c.textContent??String(c)).join('');}
  append(...children){this.children.push(...children);}
  replaceChildren(...children){this.children=children;this.ownText='';}
  addEventListener(name,handler){this.listeners[name]=handler;}
  setAttribute(){} removeAttribute(){} focus(){} reset(){}
  showModal(){this.open=true;} close(){this.open=false;}
  querySelector(selector){if(!this.fields.has(selector))this.fields.set(selector,new Element(selector));return this.fields.get(selector);}
  querySelectorAll(){return [];}
  cloneNode(){return new Element('cart-row');}
}
const deferred=()=>{let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no;});return {promise,resolve,reject};};
function workbench(requestJson=async()=>({status:200,data:[]})){
  const elements=new Map(),sessionEvents=[],windowListeners={};
  const get=selector=>{if(!elements.has(selector))elements.set(selector,new Element(selector.replace('#','')));return elements.get(selector);};
  get('#cart-line-template').content={firstElementChild:new Element('cart-template')};
  const document={addEventListener(){},querySelector:get,querySelectorAll:selector=>selector==='.cart-line'?get('#cart-lines').children:[],createElement:tag=>new Element(tag),createTextNode:text=>{const e=new Element();e.textContent=text;return e;}};
  const context={document,window:{addEventListener(type,fn){windowListeners[type]=fn;},dispatchEvent(event){sessionEvents.push(event);}},CustomEvent:class {constructor(type,options){this.type=type;this.detail=options.detail;}},location:{hash:''},Node:Element,Option:class extends Element{constructor(text,value){super();this.textContent=text;this.value=value;}},Intl,Date,Map,Set,Number,String,Error,AbortSignal,crypto:{randomUUID:()=> 'new-key'},setTimeout:()=>0,clearTimeout(){},parseQuantity,priceToCents,cartPreview,paginateRows,stockState,orderActions,orderTimeline,URLSearchParams,parseHash,queryHash,queryParams,validateRange,snapshotLabel,createDocumentUI:()=>({reset(){},show(){},createReceipt(){},createReturn(){}}),requestJson,console};
  let source=readFileSync(new URL('../../main/resources/static/app.js',import.meta.url),'utf8').replace(/^import .*;\r?\n/gm,'');
  source+='\nglobalThis.probe={state,showPage,activateSession,loadOperations,openStocktake,showOrder,withForm,openReceipt,clearSession,openOrderCommand,loadInventoryReport:typeof loadInventoryReport==="function"?loadInventoryReport:null,openAlertReceipt:typeof openAlertReceipt==="function"?openAlertReceipt:null,openStockPolicy:typeof openStockPolicy==="function"?openStockPolicy:null,loadMainList:typeof loadMainList==="function"?loadMainList:null,openProduct:typeof openProduct==="function"?openProduct:null,rememberProducts:typeof rememberProducts==="function"?rememberProducts:null,openProductPicker:typeof openProductPicker==="function"?openProductPicker:null,openProductEditor:typeof openProductEditor==="function"?openProductEditor:null,pickProduct};';
  vm.runInNewContext(source,context);
  return {get,ui:context.probe,sessionEvents,windowListeners,location:context.location,window:context.window};
}
test('入库提交期间不能重置操作标识',()=>{
  const {get,ui}=workbench();const original={skuId:1,quantity:5,operationKey:'original-key'};
  ui.state.receiptAttempt=original;get('#receipt-form').dataset.busy='true';
  get('#receipt-reset').listeners.click();
  assert.equal(ui.state.receiptAttempt,original);
});
test('下单提交期间不能用新请求标识覆盖当前尝试',()=>{
  const {get,ui}=workbench();const original={requestKey:'original-key',items:[{skuId:1,quantity:1}]};
  ui.state.orderAttempt=original;get('#order-form').dataset.busy='true';
  get('#new-order-reset').listeners.click();
  assert.equal(ui.state.orderAttempt,original);
});
test('较慢的旧订单详情不能覆盖刚打开的新订单',async()=>{
  const responses=new Map(),{get,ui}=workbench(async(...args)=>{const id=Number(args[2].split('/').at(-1));const pending=deferred();responses.set(id,pending);return pending.promise;});
  const first=ui.showOrder(1);get('#detail-dialog').close();const second=ui.showOrder(2);
  responses.get(2).resolve({data:{id:2,status:'PENDING_CONFIRM',total:2,items:[]}});await second;
  assert.equal(get('#detail-dialog-title').textContent,'订单 SF-2');
  responses.get(1).resolve({data:{id:1,status:'PENDING_CONFIRM',total:1,items:[]}});await first;
  assert.equal(get('#detail-dialog-title').textContent,'订单 SF-2');
});
test('较慢的旧详情错误不能覆盖新订单内容',async()=>{
  const responses=new Map(),{get,ui}=workbench(async(...args)=>{const id=Number(args[2].split('/').at(-1));const pending=deferred();responses.set(id,pending);return pending.promise;});
  const first=ui.showOrder(1);get('#detail-dialog').close();const second=ui.showOrder(2);
  responses.get(2).resolve({data:{id:2,status:'PENDING_CONFIRM',total:2,items:[]}});await second;
  responses.get(1).reject(new Error('old detail failed'));await first;
  assert.doesNotMatch(get('#order-detail-content').textContent,/old detail failed/);
});

test('已确认的入库关闭后，点击另一个商品应开始该商品的新操作',()=>{
  const {get,ui}=workbench();
  ui.state.me={roles:['ROLE_WAREHOUSE']};ui.state.products=[{id:1,name:'A'},{id:2,name:'B'}];
  ui.state.receiptAttempt={skuId:1,quantity:5,operationKey:'old-key'};ui.state.receiptConfirmed=true;
  ui.openReceipt(2);
  assert.equal(ui.state.receiptAttempt,null);
  assert.equal(get('#receipt-product').value,'2');
});
test('尚未确认的入库重新打开时仍保留原标识',()=>{
  const {get,ui}=workbench(),original={skuId:1,quantity:5,operationKey:'old-key'};
  ui.state.me={roles:['ROLE_WAREHOUSE']};ui.state.products=[{id:1,name:'A'},{id:2,name:'B'}];
  ui.state.receiptAttempt=original;ui.state.receiptConfirmed=false;
  ui.openReceipt(2);
  assert.equal(ui.state.receiptAttempt,original);
});


test('退出接口尚未成功时保留当前身份，成功后清空账号数据',async()=>{
 const logout=deferred();
 const {get,ui}=workbench(async(_s,method,path)=>path==='/api/auth/csrf'?{data:{token:'new-token'}}:path==='/api/auth/logout'?logout.promise:{data:[]});
 ui.state.me={id:1,username:'buyer',roles:['ROLE_BUYER']};ui.state.orders=[{id:5}];ui.state.fulfillment=[{id:8}];
 const pending=get('#sign-out').listeners.click();await new Promise(resolve=>setImmediate(resolve));
 assert.equal(ui.state.me.id,1);assert.equal(get('#sign-out').disabled,true);
 logout.resolve({status:204,data:null});await pending;
 assert.equal(ui.state.me,null);assert.deepEqual([...ui.state.orders],[]);assert.deepEqual([...ui.state.fulfillment],[]);assert.equal(ui.state.csrf,'');
});
test('退出网络失败不会只退出页面却留下服务端会话',async()=>{
 const {get,ui}=workbench(async(_s,method,path)=>{if(path==='/api/auth/csrf')return {data:{token:'token'}};throw new Error('offline');});
 ui.state.me={id:1,username:'buyer',roles:['ROLE_BUYER']};
 await get('#sign-out').listeners.click();assert.equal(ui.state.me.id,1);assert.match(get('#toast').textContent,/退出未完成/);assert.equal(get('#sign-out').disabled,false);
});
test('注册确认密码不一致时不发送注册请求',async()=>{
 let requests=0;const {get}=workbench(async()=>{requests++;return {data:[]};});
 get('#register-password').value='password-one';get('#register-confirm').value='password-two';
 await get('#register-form').listeners.submit({preventDefault(){},target:get('#register-form')});
 assert.equal(requests,0);assert.match(get('#register-error').textContent,/两次输入/);
});
test('订单状态操作提交期间不能替换待处理命令',()=>{
 const {get,ui}=workbench();const original={id:1,key:'cancel',scope:'orders'};ui.state.pendingCommand=original;
 get('#order-command-form').dataset.busy='true';ui.openOrderCommand({id:2},'confirm','orders');assert.equal(ui.state.pendingCommand,original);
});
test('订单操作失败保留原命令供重试并展示错误',async()=>{
 const {get,ui}=workbench(async()=>{throw new Error('connection-failed');});
 const original={id:1,key:'cancel',scope:'orders'};ui.state.pendingCommand=original;
 await get('#order-command-form').listeners.submit({preventDefault(){},target:get('#order-command-form')});
 assert.equal(ui.state.pendingCommand,original);assert.match(get('#command-error').textContent,/connection-failed/);
 assert.equal(get('#order-command-form').dataset.busy,'false');
});


test('库存流水筛选只接收最新请求结果',async()=>{
  const pending=[],{ui}=workbench(async()=>{const d=deferred();pending.push(d);return d.promise;});
  ui.state.me={roles:['ROLE_WAREHOUSE']};
  const first=ui.loadInventoryReport('ledger');const second=ui.loadInventoryReport('ledger');
  pending[1].resolve({data:{items:[],nextCursor:22}});await second;
  pending[0].resolve({data:{items:[],nextCursor:99}});await first;
  assert.equal(ui.state.reports.ledger.nextCursor,22);
});
test('退出账号后旧流水结果不能恢复数据',async()=>{
  const d=deferred(),{ui}=workbench(()=>d.promise);ui.state.me={roles:['ROLE_WAREHOUSE']};
  const loading=ui.loadInventoryReport('ledger');ui.clearSession();
  d.resolve({data:{items:[{id:99}],nextCursor:null}});await loading;
  assert.equal(ui.state.reports.ledger.items.length,0);
});
test('新筛选会清空流水游标和历史页',async()=>{
  let path;const {ui}=workbench(async(...args)=>{path=args[2];return {data:{items:[],nextCursor:null}};});ui.state.me={roles:['ROLE_WAREHOUSE']};
  ui.state.reports.ledger.cursor=30;ui.state.reports.ledger.history=[null];
  await ui.loadInventoryReport('ledger');assert.equal(ui.state.reports.ledger.cursor,null);assert.equal(ui.state.reports.ledger.history.length,0);assert.doesNotMatch(path,/beforeId/);
});
test('预警商品不在最近商品列表时仍可正确入库且不污染商品统计',()=>{
  const {ui,get}=workbench();ui.state.me={roles:['ROLE_WAREHOUSE']};ui.state.products=[{id:90,name:'新商品'}];
  ui.openAlertReceipt({skuId:2,name:'较早商品',code:'OLD-2'});
  assert.equal(get('#receipt-product').value,'2');assert.equal(ui.state.products.length,1);
});

test('完整商品查询使用服务端分页与搜索，不裁剪到已加载商品',async()=>{
  let path;const {ui}=workbench(async(...args)=>{path=args[2];return {data:{items:[{id:301,name:'较早商品',code:'OLD'}],total:301,page:2,size:8,totalPages:38}};});
  ui.state.me={roles:['ROLE_WAREHOUSE']};ui.state.queries.products='较早';ui.state.lists.products.page=2;
  await ui.loadMainList('products');assert.match(path,/api\/skus\/page/);assert.match(path,/page=2/);assert.match(path,/q=/);
  assert.equal(ui.state.lists.products.total,301);assert.equal(ui.state.lists.products.items[0].id,301);
});
test('新商品筛选重置页码且较慢的旧响应不能覆盖结果',async()=>{
  const requests=[],{ui}=workbench(()=>{const d=deferred();requests.push(d);return d.promise;});ui.state.me={roles:['ROLE_WAREHOUSE']};ui.state.lists.products.page=3;
  const first=ui.loadMainList('products');const second=ui.loadMainList('products',true);
  requests[1].resolve({data:{items:[],total:2,page:1,size:8,totalPages:1}});await second;
  requests[0].resolve({data:{items:[],total:99,page:3,size:8,totalPages:13}});await first;
  assert.equal(ui.state.lists.products.page,1);assert.equal(ui.state.lists.products.total,2);
});
test('列表更新失败保留上次数据并显示错误',async()=>{
  const {ui}=workbench(async()=>{throw new Error('offline');});ui.state.me={roles:['ROLE_WAREHOUSE']};ui.state.lists.products.items=[{id:1,name:'原数据',code:'OLD',price:1,active:true}];
  await ui.loadMainList('products');assert.equal(ui.state.lists.products.items.length,1);assert.match(ui.state.lists.products.error,/offline/);assert.equal(ui.state.lists.products.loading,false);
});
test('先打开的商品详情不能覆盖后打开的商品',async()=>{
  const requests=new Map(),{ui,get}=workbench(async(...args)=>{const d=deferred();requests.set(args[2],d);return d.promise;});ui.state.me={roles:['ROLE_BUYER']};
  const first=ui.openProduct(1);const second=ui.openProduct(2);
  requests.get('/api/skus/2').resolve({data:{id:2,name:'第二件',code:'TWO',price:2,active:true}});await second;
  requests.get('/api/skus/1').resolve({data:{id:1,name:'第一件',code:'ONE',price:1,active:true}});await first;
  assert.equal(get('#product-detail-title').textContent,'第二件');
});
test('关闭商品详情后到达的响应不会重新填充详情',async()=>{
  const d=deferred(),{ui,get}=workbench(()=>d.promise);ui.state.me={roles:['ROLE_BUYER']};const loading=ui.openProduct(1);get('#product-dialog').close();
  d.resolve({data:{id:1,name:'不应显示',code:'ONE',price:1,active:true}});await loading;assert.notEqual(get('#product-detail-title').textContent,'不应显示');
});

test('商品选择器可以选择当前列表以外的商品且不改变列表统计',async()=>{
  const {ui,get}=workbench(async()=>({data:{items:[{id:301,name:'远页商品',code:'FAR-301',price:12,active:true}],total:301,page:1,size:8,totalPages:38}}));
  ui.state.me={roles:['ROLE_BUYER']};ui.state.lists.products.total=301;
  const select=new Element('selected-product');await ui.openProductPicker('cart',select);
  get('#picker-results').children[0].listeners.click();
  assert.equal(select.value,'301');assert.equal(ui.state.products.find(p=>p.id===301).name,'远页商品');assert.equal(ui.state.lists.products.total,301);assert.equal(ui.state.lists.products.items.length,0);
});
test('订单已提交时不允许商品选择器改变该订单',async()=>{
  let calls=0;const {ui,get}=workbench(async()=>{calls++;return {data:{items:[]}};});ui.state.me={roles:['ROLE_BUYER']};ui.state.orderAttempt={requestKey:'existing'};
  await ui.openProductPicker('cart',new Element());assert.equal(calls,0);assert.equal(get('#picker-dialog').open,false);
});
test('商品编辑拒绝空白名称且不会发送写入请求',async()=>{
  let calls=0;const {ui,get}=workbench(async()=>{calls++;});ui.state.me={roles:['ROLE_WAREHOUSE']};ui.openProductEditor({id:1,name:'原商品',code:'ONE',price:1,active:true});
  get('#product-edit-name').value='   ';await get('#product-edit-form').listeners.submit({preventDefault(){},target:get('#product-edit-form')});
  assert.equal(calls,0);assert.match(get('#product-edit-error').textContent,/名称不能为空/);
});

test('从商品第二页选购时给空白购物车行补充该商品选项',()=>{
  const {ui,get}=workbench();ui.state.me={roles:['ROLE_BUYER']};ui.state.products=[{id:301,name:'第二页商品',code:'FAR',price:5,active:true}];
  const row=new Element('cart-row'),select=row.querySelector('select');let selected='';
  Object.defineProperty(select,'value',{get:()=>selected,set:value=>{selected=select.children.some(option=>option.value===String(value))?String(value):'';}});
  select.append(new Element('blank'));get('#cart-lines').children=[row];
  ui.pickProduct(301);assert.equal(select.value,'301');
});

test('订单列表的筛选结果不覆盖概览最近订单',async()=>{
  const {ui}=workbench(async()=>({data:{items:[],total:0,page:1,size:8,totalPages:1}}));ui.state.me={roles:['ROLE_BUYER']};ui.state.orders=[{id:7}];ui.state.queries.orders='不存在';
  await ui.loadMainList('orders',true);assert.equal(ui.state.orders.length,1);assert.equal(ui.state.orders[0].id,7);
});
test('历史订单缺少快照时显示未知名称，不读取当前商品冒充历史',async()=>{
 const paths=[];const {get,ui}=workbench(async(_s,_m,path)=>{paths.push(path);return {data:{id:5,status:'COMPLETED',total:2,items:[{skuId:301,quantity:1,unitPrice:2}]}};});
 await ui.showOrder(5);assert.match(get('#order-detail-content').textContent,/历史商品名称未知 · SKU #301/);assert.deepEqual(paths,['/api/fulfillment/orders/5']);
});
test('订单快照名称在当前商品重命名后保持下单时内容',async()=>{
 const {get,ui}=workbench(async()=>({data:{id:5,status:'COMPLETED',total:2,items:[{skuId:301,skuName:'下单时名称',skuCode:'OLD',quantity:1,unitPrice:2}]}}));ui.state.products=[{id:301,name:'当前新名称',code:'NEW'}];
 await ui.showOrder(5);assert.match(get('#order-detail-content').textContent,/下单时名称OLD/);assert.doesNotMatch(get('#order-detail-content').textContent,/当前新名称/);
});
test('商品抽屉发起入库后刷新该商品库存和流水',async()=>{
  const {ui,get}=workbench(async(...args)=>{
    const path=args[2];
    if(path==='/api/inventory/301/receipts'||path==='/api/inventory/301')return {data:{skuId:301,onHand:15,reserved:0,available:15}};
    if(path==='/api/skus/301')return {data:{id:301,name:'目标商品',code:'TARGET',price:1,active:true}};
    if(path.startsWith('/api/inventory/ledger'))return {data:{items:[],nextCursor:null}};
    if(path==='/api/dashboard')return {data:{productCount:1,onHand:15,reserved:0,lowStockCount:0}};
    return {data:{items:[],total:0,page:1,size:8,totalPages:1}};
  });ui.state.me={roles:['ROLE_WAREHOUSE']};ui.state.productDetail={id:301,product:{id:301,name:'目标商品'},stock:{onHand:5},entries:[]};get('#product-dialog').open=true;
  ui.state.receiptAttempt={skuId:301,quantity:10,operationKey:'receipt-target'};
  await get('#receipt-form').listeners.submit({preventDefault(){},target:get('#receipt-form')});assert.equal(ui.state.productDetail.stock.onHand,15);
});

test('预警查询默认按已保存的商品规则，不发送统一阈值',async()=>{
 let path;const {ui}=workbench(async(...args)=>{path=args[2];return {data:{items:[],total:0,nextCursor:null}};});ui.state.me={roles:['ROLE_WAREHOUSE']};
 await ui.loadInventoryReport('alerts');assert.doesNotMatch(path,/threshold=/);
});
test('预警展示商品安全线和补货差额',async()=>{
 const {ui,get}=workbench(async()=>({data:{items:[{skuId:1,name:'杯子',code:'CUP',available:12,onHand:14,reserved:2,safetyStock:20,threshold:20,shortage:8}],total:1,nextCursor:null}}));ui.state.me={roles:['ROLE_WAREHOUSE']};
 await ui.loadInventoryReport('alerts');assert.match(get('#alerts-body').textContent,/20/);assert.match(get('#alerts-body').textContent,/8/);
});
test('安全库存非法输入不发出写请求，正在保存时不能切换商品',async()=>{
 let calls=0;const {ui,get}=workbench(async()=>{calls++;});ui.state.me={roles:['ROLE_WAREHOUSE']};assert.equal(typeof ui.openStockPolicy,'function');
 ui.openStockPolicy({skuId:1,name:'杯子',code:'CUP',safetyStock:20,available:12});get('#policy-safety-stock').value='1.5';
 await get('#policy-form').listeners.submit({preventDefault(){},target:get('#policy-form')});assert.equal(calls,0);assert.match(get('#policy-error').textContent,/数量/);
 get('#policy-form').dataset.busy='true';ui.openStockPolicy({skuId:2,name:'其他',safetyStock:30,available:1});assert.equal(ui.state.stockPolicy.skuId,1);
});
test('预警入库使用补货差额，重试时保留原入库数量',()=>{
 const {ui,get}=workbench();ui.state.me={roles:['ROLE_WAREHOUSE']};ui.state.products=[{id:1,name:'杯子'}];
 ui.openAlertReceipt({skuId:1,name:'杯子',shortage:8});assert.equal(get('#receipt-form input[name=quantity]').value,'8');
 ui.state.receiptAttempt={skuId:1,quantity:5,operationKey:'retry'};get('#receipt-form input[name=quantity]').value='5';
 ui.openAlertReceipt({skuId:1,name:'杯子',shortage:8});assert.equal(get('#receipt-form input[name=quantity]').value,'5');
});

test('流水更新与网络失败保留原行，重试沿用同一游标',async()=>{
 const pending=deferred();let lastPath;const {ui}=workbench(async(...args)=>{lastPath=args[2];return pending.promise;});ui.state.me={roles:['ROLE_WAREHOUSE']};const report=ui.state.reports.ledger;
 report.items=[{id:1,name:'原商品',code:'ORIGINAL',operationType:'INBOUND',onHandDelta:2,reservedDelta:0,actorId:3}];report.cursor=25;report.history=[null];
 const loading=ui.loadInventoryReport('ledger',false);assert.equal(report.items.length,1);pending.reject(new Error('offline'));await loading;
 assert.equal(report.items[0].id,1);assert.equal(report.cursor,25);assert.equal(report.history.length,1);assert.match(lastPath,/beforeId=25/);assert.match(report.error,/offline/);
});
test('盘点列表只采用最新查询结果，失败保留旧页数据',async()=>{
 const queue=[],{ui}=workbench(()=>{const d=deferred();queue.push(d);return d.promise;});ui.state.me={roles:['ROLE_WAREHOUSE']};
 const a=ui.loadOperations('stocktakes'),b=ui.loadOperations('stocktakes',true);queue[1].resolve({data:{items:[],page:1,size:8,total:2,totalPages:1}});await b;queue[0].resolve({data:{items:[],page:2,size:8,total:50,totalPages:7}});await a;assert.equal(ui.state.operations.stocktakes.total,2);
 const c=ui.loadOperations('stocktakes');queue[2].reject(new Error('offline'));await c;assert.equal(ui.state.operations.stocktakes.total,2);assert.match(ui.state.operations.stocktakes.error,/offline/);
});
test('退出后盘点旧响应不能重新填充业务数据',async()=>{
 const d=deferred(),{ui}=workbench(()=>d.promise);ui.state.me={roles:['ROLE_WAREHOUSE']};const p=ui.loadOperations('audit');ui.clearSession();d.resolve({data:{items:[{id:9}],total:1}});await p;assert.equal(ui.state.operations.audit.items.length,0);
});
test('旧盘点详情不能覆盖后打开的盘点',async()=>{
 const queue=new Map(),{ui,get}=workbench(async(...args)=>{const d=deferred();queue.set(args[2],d);return d.promise;});ui.state.me={roles:['ROLE_WAREHOUSE']};
 const a=ui.openStocktake(1),b=ui.openStocktake(2);queue.get('/api/stocktakes/2').resolve({data:{id:2,skuCode:'B',skuName:'第二商品',status:'DRAFT'}});await b;queue.get('/api/stocktakes/1').resolve({data:{id:1,skuCode:'A',skuName:'第一商品',status:'DRAFT'}});await a;assert.equal(ui.state.stocktakeDetail.id,2);assert.match(get('#stocktake-detail-content').textContent,/第二商品/);
});
test('盘点创建网络失败保留原内容与请求标识，提交中不能开始另一笔',async()=>{
 const d=deferred(),{ui,get}=workbench(()=>d.promise);ui.state.me={roles:['ROLE_WAREHOUSE']};get('#stocktake-product').value='1';get('#stocktake-target').value='0';get('#stocktake-reason').value='实盘为零';
 const pending=get('#stocktake-form').listeners.submit({preventDefault(){},target:get('#stocktake-form')});const original=ui.state.stocktakeAttempt;assert.equal(original.targetOnHand,0);get('#stocktake-new-attempt').listeners.click();assert.equal(ui.state.stocktakeAttempt,original);
 d.reject(new Error('offline'));await pending;assert.equal(ui.state.stocktakeAttempt,original);assert.equal(get('#stocktake-inputs').disabled,true);assert.match(get('#stocktake-error').textContent,/offline/);
});
test('盘点确认进行中不能切换为另一张盘点',async()=>{
 const d=deferred(),{ui,get}=workbench(()=>d.promise);ui.state.me={roles:['ROLE_WAREHOUSE']};const original={id:3,status:'DRAFT'};ui.state.stocktakeDetail=original;
 const p=get('#stocktake-command-form').listeners.submit({preventDefault(){},submitter:{value:'confirm'},target:get('#stocktake-command-form')});await ui.openStocktake(4);assert.equal(ui.state.stocktakeDetail,original);assert.equal(get('#stocktake-cancel').disabled,true);d.reject(new Error('stock changed'));await p;assert.equal(get('#stocktake-cancel').disabled,false);assert.match(get('#stocktake-command-error').textContent,/stock changed/);
});

test('聊天会话在退出时发布清空，准备完成后可取得当前身份',()=>{
  const {ui,sessionEvents,windowListeners}=workbench();
  ui.state.me={id:1,username:'buyer',roles:['ROLE_BUYER']};ui.state.epoch=7;
  windowListeners['stockflow:chat-ready']();
  assert.equal(sessionEvents.at(-1).detail.me.username,'buyer');
  assert.equal(sessionEvents.at(-1).detail.epoch,7);
  ui.clearSession();
  assert.equal(sessionEvents.at(-1).type,'stockflow:session');
  assert.equal(sessionEvents.at(-1).detail.me,null);
  assert.equal(sessionEvents.at(-1).detail.epoch,8);
});

test('刷新或后退到带查询的 hash 会恢复控件、页码和服务端日期金额查询',async()=>{
 const paths=[];const {get,ui,location}=workbench(async(_s,_m,path)=>{paths.push(path);return {data:{items:[],total:0,page:3,size:20,totalPages:3}};});
 ui.state.me={id:1,roles:['ROLE_BUYER']};ui.state.loaded=true;location.hash='#orders?q=SF-99&status=COMPLETED&page=3&size=20&from=2026-10-01&to=2026-10-27&minTotal=1.20&maxTotal=9.00';ui.showPage();await new Promise(resolve=>setImmediate(resolve));
 assert.equal(get('#orders-search').value,'SF-99');assert.equal(get('#orders-status').value,'COMPLETED');assert.equal(ui.state.lists.orders.page,3);assert.match(paths.at(-1),/from=2026-10-01/);assert.match(paths.at(-1),/minTotal=1.20/);
 location.hash='#orders?q=older&page=1&size=8';ui.showPage();assert.equal(get('#orders-search').value,'older');assert.equal(get('#orders-from').value,'');assert.equal(get('#orders-minTotal').value,'');
});
test('日期范围无效时保留列表并展示错误，不发送请求',async()=>{
 let calls=0;const {get,ui}=workbench(async()=>{calls++;return {data:{items:[]}};});ui.state.me={roles:['ROLE_BUYER']};get('#orders-from').value='2026-10-27';get('#orders-to').value='2026-10-01';await ui.loadMainList('orders');assert.equal(calls,0);assert.match(ui.state.lists.orders.error,/日期/);
});

test('浏览器禁用存储时仍能登录并显示工作台',async()=>{
 const {ui,get,window}=workbench(async(_s,_m,path)=>({data:path.includes('/page')?{items:[],total:0,page:1,size:8,totalPages:1}:path==='/api/dashboard'?{productCount:0,lowStockCount:0,onHand:0,reserved:0}:[]}));
 Object.defineProperty(window,'sessionStorage',{get(){throw new Error('storage disabled');}});await ui.activateSession({id:3,username:'warehouse',roles:['ROLE_WAREHOUSE']});assert.equal(get('#workspace').hidden,false);assert.equal(ui.state.me.id,3);
});
test('对账默认只显示差异，显式 false 才显示全部',()=>{const {ui,get,location}=workbench();ui.state.me={roles:['ROLE_WAREHOUSE']};location.hash='#reconciliation';ui.showPage();assert.equal(get('#reconciliation-only').checked,true);location.hash='#reconciliation?mismatchOnly=false';ui.showPage();assert.equal(get('#reconciliation-only').checked,false);});
