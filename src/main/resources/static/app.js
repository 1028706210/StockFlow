import {parseQuantity,priceToCents,cartPreview,paginateRows,stockState,orderActions,orderTimeline} from '/ui-model.mjs';

import {requestJson} from '/api-client.mjs';

const $ = selector => document.querySelector(selector);
const all = selector => [...document.querySelectorAll(selector)];
const money = value => new Intl.NumberFormat('zh-CN',{style:'currency',currency:'CNY'}).format(value);
const time = value => value ? new Intl.DateTimeFormat('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date(value)) : '—';
const pages = {
  stocktakes:['库存盘点','STOCK COUNTS','记录实盘数量，确认差异并追踪调整。'],
  reconciliation:['库存对账','RECONCILIATION','只读核对库存、有效订单与累计流水。'],
  audit:['操作审计','OPERATION AUDIT','查看商品与库存规则的变更前后值。'],
  overview:['工作概览','WORKSPACE OVERVIEW','从当前业务数据，了解库存与订单的状态。'],
  products:['商品管理','PRODUCT CATALOG','统一管理商品名称、编码与销售价格。'],
  inventory:['库存管理','INVENTORY CONTROL','查看实存与预留，为商品补充可用库存。'],
  ledger:['库存流水','INVENTORY JOURNAL','追踪每一笔库存变动，核对商品与关联订单。'],
  alerts:['库存预警','STOCK ALERTS','主动发现库存不足，从预警直接补充库存。'],
  fulfillment:['订单履约','ORDER FULFILLMENT','查询订单状态，为已确认的订单完成发货。'],
  orders:['我的订单','MY ORDERS','查看订单进度、金额与商品明细。'],
  'new-order':['创建订单','CREATE AN ORDER','选择商品和数量，提交一笔新的订单。']
};
function newInventoryReports(){return Object.fromEntries(['ledger','alerts'].map(kind=>[kind,{items:[],cursor:null,history:[],nextCursor:null,sequence:0,loading:false,error:'',total:0,query:'',type:''}]));}
function newMainLists(){return Object.fromEntries(['products','inventory','orders','fulfillment'].map(kind=>[kind,{items:[],total:0,page:1,size:8,totalPages:1,loading:false,error:'',sequence:0}]));}
const state = {operations:newOperations(),stocktakeAttempt:null,stocktakeDetail:null,trends:[],trendsError:'',lists:newMainLists(),dashboard:null,productStatus:'all',productDetail:null,editingProduct:null,stockPolicy:null,picker:null,reports:newInventoryReports(),csrf:'',me:null,epoch:0,refreshId:0,loading:false,loaded:false,error:'',products:[],stocks:[],orders:[],fulfillment:[],pendingCommand:null,detailOrderId:null,
  queries:{products:'',inventory:'',orders:'',fulfillment:''},page:{products:1,inventory:1,orders:1,fulfillment:1},stockFilter:'all',ordersStatus:'all',fulfillmentStatus:'all',orderAttempt:null,lastOrder:null,receiptAttempt:null,receiptConfirmed:false};
const warehouse = () => state.me?.roles.some(r => r==='ROLE_WAREHOUSE' || r==='ROLE_ADMIN');
const buyer = () => state.me?.roles.includes('ROLE_BUYER');
let toastTimer;
function node(tag,text,className) {
  const item=document.createElement(tag);
  if(text!==undefined)item.textContent=text;
  if(className)item.className=className;
  return item;
}
function button(text,action,className='text-button') {
  const item=node('button',text,className);item.type='button';item.addEventListener('click',action);return item;
}
function toast(message,error=false) {
  clearTimeout(toastTimer);$('#toast').textContent=message;$('#toast').className='toast'+(error?' error':'');$('#toast').hidden=false;
  toastTimer=setTimeout(()=>{$('#toast').hidden=true;},6500);
}
function message(selector,text='') {$(selector).textContent=text;$(selector).hidden=!text;}
function failure(error) {return error.message || '操作未完成，请重试。';}
async function request(method,path,body) {
  try{return await requestJson(()=>state,method,path,body);}
  catch(error){
    if(!error.stale && state.me && error.status===401){clearSession('登录已过期，请重新登录。');error.stale=true;}
    else if(!error.stale && state.me && error.status===403){
      try{await requestJson(()=>state,'GET','/api/auth/me');}
      catch(probe){if(!probe.stale&&probe.status===401){clearSession('登录已过期，请重新登录。');error.stale=true;}}
    }
    throw error;
  }
}
async function ensureCsrf(force=false){
  if(!force&&state.csrf)return;
  const {data}=await request('GET','/api/auth/csrf');state.csrf=data.token;
}
function clearSession(note=''){
  state.epoch++;state.refreshId++;state.lists=newMainLists();state.operations=newOperations();state.stocktakeAttempt=null;state.stocktakeDetail=null;stocktakeSequence++;state.trends=[];state.trendsError='';state.dashboard=null;state.productDetail=null;state.editingProduct=null;state.stockPolicy=null;state.picker=null;productSequence++;pickerSequence++;state.reports=newInventoryReports();state.csrf='';state.me=null;state.products=[];state.stocks=[];state.orders=[];state.fulfillment=[];
  state.loaded=false;state.loading=false;state.error='';state.orderAttempt=null;state.lastOrder=null;state.receiptAttempt=null;state.receiptConfirmed=false;state.pendingCommand=null;state.detailOrderId=null;detailSequence++;
  all('form').forEach(form=>{form.dataset.operation='';form.dataset.busy='false';form.removeAttribute('aria-busy');const submit=form.querySelector('[type=submit]');if(submit)submit.disabled=false;});
  $('#stocktake-cancel').disabled=false;$('#receipt-reset').disabled=false;$('#new-order-reset').disabled=false;$('#sign-out').disabled=false;
  all('dialog[open]').forEach(dialog=>dialog.close());closeMenu();$('#workspace').hidden=true;$('#login-screen').hidden=false;$('#toast').hidden=true;
  $('#login-password').value='';$('#register-password').value='';$('#register-confirm').value='';$('#cart-lines').replaceChildren();setAuthForm(false);message('#login-error',note);$('#login-password').focus();
}
function setAuthForm(register){
  $('#login-form').hidden=register;$('#register-form').hidden=!register;$('#show-register').hidden=register;$('#show-login').hidden=!register;
  $('#auth-title').textContent=register?'注册买家账号':'进入工作台';$('#auth-description').textContent=register?'建立自己的账号，开始管理你的订单。':'登录账号，开始处理业务。';
  message('#login-error');message('#register-error');
}
async function activateSession(me){
  state.me=me;state.epoch++;state.lists=newMainLists();state.operations=newOperations();state.stocktakeAttempt=null;state.stocktakeDetail=null;stocktakeSequence++;state.trends=[];state.trendsError='';state.dashboard=null;state.productStatus='all';$('#products-status').value='all';state.reports=newInventoryReports();$('#ledger-search').value='';$('#alerts-search').value='';$('#ledger-type').value='';state.pendingCommand=null;state.detailOrderId=null;
  $('#login-password').value='';$('#register-password').value='';$('#register-confirm').value='';$('#login-screen').hidden=true;$('#workspace').hidden=false;
  $('#account-name').textContent=me.username;$('#account-role').textContent=warehouse()?(me.roles.includes('ROLE_ADMIN')?'管理员':'仓库管理'):'买家';$('#avatar').textContent=me.username.slice(0,1).toUpperCase();
  all('[data-role]').forEach(item=>{item.hidden=item.dataset.role==='buyer'?!buyer():!warehouse();});all('[data-filter]').forEach(input=>{input.value='';});
  state.queries={products:'',inventory:'',orders:'',fulfillment:''};state.page={products:1,inventory:1,orders:1,fulfillment:1};state.stockFilter='all';state.ordersStatus='all';state.fulfillmentStatus='all';
  $('#inventory-status').value='all';$('#orders-status').value='all';$('#fulfillment-status').value='all';resetOrder();showPage();await refreshData();
}
async function restoreSession(){
  try{const {data}=await request('GET','/api/auth/me');await ensureCsrf(true);await activateSession(data);}
  catch(error){if(!error.stale&&error.status!==401)message('#login-error',failure(error));}
  finally{$('#login-form button[type=submit]').disabled=false;$('#register-form button[type=submit]').disabled=false;}
}

let formSequence=0;
let detailSequence=0;
let productSequence=0;
let pickerSequence=0;
const searchTimers=new Map();
async function withForm(form,errorSelector,work) {
  if(form.dataset.busy==='true')return;
  const operation=String(++formSequence);form.dataset.operation=operation;
  form.dataset.busy='true';message(errorSelector);
  const submit=form.querySelector('[type=submit]');submit.disabled=true;form.setAttribute('aria-busy','true');
  const reset=form.id==='receipt-form'?$('#receipt-reset'):form.id==='order-form'?$('#new-order-reset'):null;
  if(reset)reset.disabled=true;
  const epoch=state.epoch;
  try {await work();} catch(error) {if(!error.stale && epoch===state.epoch)message(errorSelector,failure(error));}
  finally {if(form.dataset.operation===operation){form.dataset.busy='false';form.removeAttribute('aria-busy');submit.disabled=false;if(reset)reset.disabled=false;if(form.id==='order-form')updateCart();}}
}
function navigate(page){if(location.hash==='#'+page)showPage();else location.hash=page;}
function closeMenu(){$('#sidebar').classList.remove('open');$('#sidebar-backdrop').hidden=true;$('#menu-toggle').setAttribute('aria-expanded','false');}
function showPage(){
  if(!state.me)return;
  let page=location.hash.slice(1);
  if(!pages[page] || (['inventory','fulfillment','ledger','alerts','stocktakes','reconciliation','audit'].includes(page)&&!warehouse()) || (['orders','new-order'].includes(page)&&!buyer()))page='overview';
  $('#workspace').dataset.page=page;
  all('.view').forEach(view=>{view.hidden=view.id!=='view-'+page;});
  all('[data-nav]').forEach(item=>{item.classList.toggle('active',item.dataset.nav===page);if(item.dataset.nav===page)item.setAttribute('aria-current','page');else item.removeAttribute('aria-current');});
  $('#page-title').textContent=pages[page][0];$('#breadcrumb-page').textContent=pages[page][0];
  $('#page-kicker').textContent=pages[page][1];$('#page-description').textContent=pages[page][2];
  const action=$('#heading-action');action.hidden=false;
  if(page==='products'&&warehouse()){action.textContent='新增商品';action.onclick=openSku;}
  else if(page==='stocktakes'&&warehouse()){action.textContent='建立盘点';action.onclick=()=>openStocktakeCreate();}
  else if(page==='inventory'&&warehouse()){action.textContent='商品入库';action.onclick=()=>openReceipt();}
  else if((page==='orders'||page==='overview')&&buyer()){action.textContent='创建订单';action.onclick=()=>navigate('new-order');}
  else if(page==='overview'&&warehouse()){action.textContent='新增商品';action.onclick=openSku;}
  else action.hidden=true;
  closeMenu();
  if(state.operations[page])loadOperations(page);
  else if(['ledger','alerts'].includes(page))loadInventoryReport(page);
  else if(state.loaded&&state.lists[page])loadMainList(page);
}
async function refreshData(){
  if(!state.me)return;
  const epoch=state.epoch,refreshId=++state.refreshId;
  state.loading=true;$('#refresh-data').disabled=true;$('#loading-note').hidden=false;message('#load-error');
  try{
    const jobs=['products',...(warehouse()?['inventory','fulfillment']:[]),...(buyer()?['orders']:[])].map(kind=>loadMainList(kind));
    const recentPath=warehouse()?'/api/inventory/page?page=1&size=5&status=all':'/api/orders/page?page=1&size=5&status=all';
    const trendJob=request('GET','/api/dashboard/trends').then(result=>({data:result.data,error:''})).catch(error=>({data:null,error:failure(error)}));
    const [{data:dashboard},{data:recent},trend]=await Promise.all([request('GET','/api/dashboard'),request('GET',recentPath),trendJob,...jobs]);
    if(epoch!==state.epoch||refreshId!==state.refreshId)return;
    if(Array.isArray(trend.data))state.trends=trend.data;state.trendsError=trend.error;state.dashboard=dashboard;if(warehouse())state.stocks=recent.items;else state.orders=recent.items;state.loaded=true;state.error='';
    $('#updated-at').textContent='更新于 '+new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false});
    rebuildCartOptions();
  }catch(error){
    if(error.stale||epoch!==state.epoch||refreshId!==state.refreshId)return;
    state.error=failure(error);message('#load-error','汇总更新失败，保留上次数据：'+state.error);
  }finally{
    if(epoch===state.epoch&&refreshId===state.refreshId){state.loading=false;$('#refresh-data').disabled=false;$('#loading-note').hidden=true;render();updateCart();if(warehouse()&&['ledger','alerts'].includes(location.hash.slice(1)))loadInventoryReport(location.hash.slice(1));if(warehouse()&&state.operations[location.hash.slice(1)])loadOperations(location.hash.slice(1));}
  }
}
function rememberProducts(products){
  const cached=new Map(state.products.map(product=>[product.id,product]));for(const product of products)cached.set(product.id,product);state.products=[...cached.values()];
}
async function loadMainList(kind,reset=false){
  if(!state.me||(kind!=='products'&&kind!=='orders'&&!warehouse())||(kind==='orders'&&!buyer()))return;
  const list=state.lists[kind],epoch=state.epoch,sequence=++list.sequence;if(reset)list.page=1;
  const paths={products:'/api/skus/page',inventory:'/api/inventory/page',orders:'/api/orders/page',fulfillment:'/api/fulfillment/orders/page'};
  const status=kind==='products'?state.productStatus:kind==='inventory'?state.stockFilter:state[kind+'Status'];
  const params=new URLSearchParams({page:String(list.page),size:String(list.size),q:state.queries[kind],status:status||'all'});
  list.loading=true;list.error='';renderTable(kind);
  try{
    const {data}=await request('GET',paths[kind]+'?'+params);
    if(epoch!==state.epoch||list!==state.lists[kind]||sequence!==list.sequence)return;
    Object.assign(list,{items:data.items,total:data.total,page:data.page,size:data.size,totalPages:data.totalPages});
    if(kind==='products')rememberProducts(data.items);
    if(kind==='inventory')rememberProducts(data.items.map(row=>({id:row.skuId,code:row.code,name:row.name,price:row.price,active:row.active})));

  }catch(error){if(error.stale||epoch!==state.epoch||list!==state.lists[kind]||sequence!==list.sequence)return;list.error=failure(error);}
  finally{if(epoch===state.epoch&&list===state.lists[kind]&&sequence===list.sequence){list.loading=false;renderTable(kind);}}
}
function badge(label,tone='neutral'){return node('span',label,'badge '+tone);}
function orderBadge(status) {
  const labels={PENDING_CONFIRM:['待确认','pending'],CONFIRMED:['已确认','success'],CANCELLED:['已取消','neutral'],SHIPPED:['已发货','success'],COMPLETED:['已完成','success']};
  const [label,tone]=labels[status]||[status,'neutral'];return badge(label,tone);
}
function cell(row,text,className){const td=node('td',undefined,className);if(text instanceof Node)td.append(text);else td.textContent=text;row.append(td);return td;}
function productCell(product,code){
  const value=node('div');value.append(node('strong',product?.name || '商品 #'+(product?.id || '—')));
  if(code)value.append(node('span',code,'subtext code'));return value;
}
function empty(target,title,description,action){
  const container=$(target);container.replaceChildren(node('strong',title),node('p',description));
  if(action)container.append(action);container.hidden=false;
}
function render(){
  if(!state.me)return;renderOverview();for(const kind of ['products','inventory','orders','fulfillment'])renderTable(kind);
}
function renderOverview(){
  renderTasksAndTrend();
  const valid=!!state.dashboard;const summary=state.dashboard||{};
  $('#hero-summary').textContent=!valid?'业务汇总待加载':warehouse()?summary.lowStockCount+' 种商品低于安全库存':summary.pendingCount+' 笔订单等待确认';
  const metrics=warehouse()?[
    ['商品种类',summary.productCount,'CATALOG','全部商品，含停用商品',''],
    ['实存总量',summary.onHand,'ON HAND','全库实存数量',''],
    ['已预留量',summary.reserved,'RESERVED','全库订单占用数量','accent'],
    ['低库存商品',summary.lowStockCount,'ATTENTION','在售商品，可用量低于各自安全线','warning']
  ]:[
    ['在售商品',summary.productCount,'CATALOG','全部在售商品',''],
    ['我的订单',summary.orderCount,'MY ORDERS','全部本人订单',''],
    ['待确认订单',summary.pendingCount,'PENDING','本人待确认订单','accent'],
    ['订单总额',money(summary.orderTotal||0),'ORDER VALUE','全部本人订单金额，含已取消订单','']
  ];
  $('#metrics').replaceChildren(...metrics.map(([label,value,kicker,note,tone],index)=>{
    const item=node('div',undefined,'metric '+tone),heading=node('div',label,'metric-label');heading.append(node('span',kicker));
    const icon=node('i',undefined,'icon icon-'+['package','warehouse','shopping-bag','arrow-up-right'][index]);icon.setAttribute('aria-hidden','true');const symbol=node('span',undefined,'metric-symbol');symbol.append(icon);item.append(symbol,heading,node('div',valid?String(value):'—','metric-value'),node('p',note,'metric-note'));return item;
  }));
  $('#recent-title').textContent=warehouse()?'库存概况':'最近订单';
  $('#overview-more').onclick=()=>navigate(warehouse()?'inventory':'orders');
  const head=node('tr');(warehouse()?['商品 / 编码','可用量','状态']:['订单号','订单金额','状态']).forEach(text=>head.append(node('th',text)));
  $('#recent-head').replaceChildren(head);$('#recent-body').replaceChildren();
  const rows=(warehouse()?state.stocks:state.orders).slice(0,5);
  for(const item of rows){
    const row=node('tr');
    if(warehouse()){
      const product=state.products.find(p=>p.id===item.skuId);
      cell(row,productCell(product||{id:item.skuId,name:item.name},item.code));cell(row,String(item.available),'number');
      const status=stockState(item);cell(row,badge(status.label,status.tone));
    }else{cell(row,copyIdentifier('SF-'+item.id));cell(row,money(item.total),'number');cell(row,orderBadge(item.status));}
    $('#recent-body').append(row);
  }
  $('#recent-empty').hidden=rows.length>0;
  if(!rows.length)empty('#recent-empty',state.error?'数据暂不可用':warehouse()?'还没有库存记录':'还没有订单',state.error?'请检查服务连接后，点击刷新数据。':warehouse()?'先创建商品，再为商品完成第一笔入库。':'选择商品并提交后，可以在这里查看订单。');
  $('#quick-description').textContent=warehouse()?'让库存准备就绪，再交给买家下单。':'从选购到收货，跟踪每一笔订单。';
  $('#quick-actions').replaceChildren(...(warehouse()?[
    button('新增商品',openSku,'button secondary'),button('为商品入库',()=>openReceipt(),'button primary'),button('处理订单履约',()=>navigate('fulfillment'),'button secondary')
  ]:[
    button('浏览商品',()=>navigate('products'),'button secondary'),button('创建订单',()=>navigate('new-order'),'button primary')
  ]));
}
function renderTable(kind){
  const list=state.lists[kind],body=$('#'+kind+'-body');body.replaceChildren();
  const unavailable=list.loading||!!list.error;
  const action=(text,fn,cls='text-button')=>{const control=button(text,fn,cls);control.disabled=unavailable;return control;};
  for(const item of list.items){
    const row=node('tr');
    if(kind==='products'||kind==='inventory'){
      const id=kind==='products'?item.id:item.skuId;
      const title=node('div');title.append(action(item.name,()=>openProduct(id),'row-title'));if(kind==='inventory')title.append(copyIdentifier(item.code));cell(row,title);
      if(kind==='products'){
        cell(row,copyIdentifier(item.code));cell(row,money(item.price),'number');cell(row,badge(item.active?'在售':'停用',item.active?'success':'neutral'));
        const actions=node('div',undefined,'cell-actions');actions.append(action('详情',()=>openProduct(id)));
        if(warehouse())actions.append(action('编辑',()=>openProductEditor(item)),action('入库',()=>openAlertReceipt({...item,skuId:id})));
        else if(item.active)actions.append(action('选购',()=>pickProduct(id)));
        cell(row,actions,'right');
      }else{
        cell(row,String(item.onHand),'number');cell(row,String(item.reserved),'number');cell(row,node('strong',String(item.available)),'number');const status=stockState(item);cell(row,badge(status.label,status.tone));const controls=node('div',undefined,'cell-actions');controls.append(action('安全库存',()=>openStockPolicy(item)),action('入库',()=>openAlertReceipt(item)));cell(row,controls,'right');
      }
    }else{
      cell(row,copyIdentifier('SF-'+item.id));if(kind==='fulfillment')cell(row,'买家 #'+item.buyerId);
      const status=node('div');status.append(orderBadge(item.status));if(item.cancelReason)status.append(node('span',item.cancelReason==='EXPIRED'?'超时取消':'买家取消','subtext'));cell(row,status);cell(row,money(item.total),'number');cell(row,time(item.expiresAt));
      const actions=node('div',undefined,'cell-actions');actions.append(action('查看详情',()=>showOrder(item.id,kind)));for(const key of orderActions(item.status,kind==='fulfillment'?'WAREHOUSE':'BUYER'))actions.append(action(commandLabels[key],()=>openOrderCommand(item,key,kind)));cell(row,actions,'right');
    }body.append(row);
  }
  $('#'+kind+'-body').classList.toggle('table-updating',list.loading);
  message('#'+kind+'-query-note',list.loading?'正在更新，暂时保留上次结果…':list.error?'更新失败，保留上次结果：'+list.error:'');
  $('#'+kind+'-empty').hidden=list.items.length>0||list.loading||!!list.error;
  if(!list.items.length&&!list.loading&&!list.error)empty('#'+kind+'-empty','没有匹配的记录','调整关键词或筛选条件后再查询。');
  const previous=button('上一页',()=>{list.page--;loadMainList(kind);},'');previous.disabled=unavailable||list.page<=1;
  const next=button('下一页',()=>{list.page++;loadMainList(kind);},'');next.disabled=unavailable||list.page>=list.totalPages;
  const controls=node('div');controls.append(previous,node('span',list.page+' / '+list.totalPages),next);
  if(list.error)controls.append(button('重试',()=>loadMainList(kind),'text-button'));
  const size=node('select');size.setAttribute('aria-label',pages[kind][0]+'每页条数');for(const value of [8,20,50])size.append(new Option('每页 '+value+' 条',String(value)));size.value=String(list.size);size.disabled=list.loading;size.addEventListener('change',()=>{list.size=Number(size.value);loadMainList(kind,true);});controls.append(size,button('重置筛选',()=>resetList(kind)));
  $('#'+kind+'-pagination').replaceChildren(node('span','共 '+list.total+' 条 · 每页 '+list.size+' 条'),controls);
  if(kind!=='inventory')$('#'+kind+'-loaded').textContent='共 '+list.total+' 条 · 全量查询';
}
function productOptions(select,selected='',onlyActive=false){
  select.replaceChildren(new Option('请选择商品',''));
  for(const p of state.products.filter(p=>!onlyActive||p.active))select.append(new Option(p.name+' · '+p.code+(onlyActive?' · '+money(p.price):''),String(p.id)));
  select.value=String(selected);
}
function openSku(){message('#sku-error');$('#sku-form').reset();$('#sku-dialog').showModal();}
function openReceipt(id,fallback){
  if(!warehouse())return;
  if(!state.products.length&&!fallback){toast('请先创建商品，或刷新商品列表。',true);return;}
  if(!state.receiptAttempt || state.receiptConfirmed)resetReceipt(id);
  else if(id && id!==state.receiptAttempt.skuId)toast('上一笔入库尚未确认，请先重试原操作，或明确开始新一笔。',true);
  $('#receipt-dialog').showModal();
}
function openAlertReceipt(item){
  if(!warehouse()||$('#receipt-form').dataset.busy==='true')return;
  openReceipt(item.skuId,item);
  if(state.receiptAttempt)return;
  if(!state.products.some(product=>product.id===item.skuId)){
    $('#receipt-product').append(new Option(item.name+' · '+item.code,String(item.skuId)));
    $('#receipt-product').value=String(item.skuId);
  }
  if(Number.isInteger(item.shortage)&&item.shortage>0)$('#receipt-form input[name=quantity]').value=String(Math.min(item.shortage,1000000));
}
function resetReceipt(id){
  if($('#receipt-form').dataset.busy==='true')return;
  state.receiptAttempt=null;state.receiptConfirmed=false;$('#receipt-form').reset();productOptions($('#receipt-product'),id||state.products[0]?.id);
  $('#receipt-search-trigger').disabled=false;$('#receipt-key').value=crypto.randomUUID();$('#receipt-product').disabled=false;$('#receipt-form input[name=quantity]').disabled=false;
  message('#receipt-error');message('#receipt-success');$('#receipt-submit').textContent='确认入库';$('#receipt-reset').hidden=true;
}
function cartRows(){return all('.cart-line').map(row=>({skuId:row.querySelector('.cart-product').value,quantity:row.querySelector('.cart-quantity').value}));}
function appendCartLine(id='',quantity=1){
  if(all('.cart-line').length>=10){toast('最多添加 10 行商品。',true);return;}
  const row=$('#cart-line-template').content.firstElementChild.cloneNode(true);
  productOptions(row.querySelector('select'),id,true);row.querySelector('label').append(button('搜索全部商品',()=>openProductPicker('cart',row.querySelector('select')),'text-button cart-search'));row.querySelector('input').value=quantity;$('#cart-lines').append(row);updateCart();
}
function rebuildCartOptions(){
  for(const row of all('.cart-line')){const select=row.querySelector('select');productOptions(select,select.value,true);}
  if(!all('.cart-line').length)appendCartLine();
}
function resetOrder(){
  if($('#order-form').dataset.busy==='true')return;
  if(state.orderAttempt&&!state.lastOrder)toast('新请求标识已生成；请先在“我的订单”核对上一笔提交结果。');
  state.orderAttempt=null;state.lastOrder=null;$('#cart-fieldset').disabled=false;$('#order-key').value=crypto.randomUUID();
  $('#cart-lines').replaceChildren();appendCartLine();message('#order-success');$('#submit-order').textContent='提交订单';updateCart();
}
function pickProduct(id){
  if(!buyer())return;
  if(state.orderAttempt){toast('当前订单已提交过，请先点击“开始另一笔订单”。');navigate('new-order');return;}
  const existing=all('.cart-line').find(row=>Number(row.querySelector('select').value)===id);
  if(existing){const input=existing.querySelector('input');input.value=Math.min(99,Number(input.value)+1);}
  else{
    const blank=all('.cart-line').find(row=>!row.querySelector('select').value);
    if(blank)productOptions(blank.querySelector('select'),id,true);else appendCartLine(id);
  }
  updateCart();navigate('new-order');toast('已加入订单明细。');
}
function updateCart(){
  if(!state.me)return;
  for(const row of all('.cart-line')){
    const product=state.products.find(p=>p.id===Number(row.querySelector('select').value));
    let total='—';try{if(product)total=money(priceToCents(product.price)*parseQuantity(row.querySelector('input').value)/100);}catch{}
    row.querySelector('.cart-subtotal').textContent=total;
  }
  let valid=false;
  try{
    const preview=cartPreview(cartRows(),state.products);
    $('#cart-kind-count').textContent=preview.items.length+' 种';$('#cart-total').textContent=money(preview.totalCents/100);
    if(!state.orderAttempt)message('#cart-error');valid=true;
  }catch(error){$('#cart-kind-count').textContent='—';$('#cart-total').textContent='—';if(!state.orderAttempt)message('#cart-error',failure(error));}
  $('#submit-order').disabled=(!valid&&!state.orderAttempt)||$('#order-form').dataset.busy==='true'||!state.loaded;
  $('#add-cart-line').disabled=all('.cart-line').length>=10;
}
async function showOrder(id,scope=buyer()?'orders':'fulfillment'){
  state.detailOrderId=id;
  const sequence=++detailSequence;
  const dialog=$('#detail-dialog');$('#order-detail-content').replaceChildren(node('p','正在加载订单…','muted'));
  if(!dialog.open)dialog.showModal();
  const epoch=state.epoch;
  try{
    const {data:order}=await request('GET',(scope==='fulfillment'?'/api/fulfillment/orders/':'/api/orders/')+id);
    if(epoch!==state.epoch || sequence!==detailSequence || !dialog.open)return;
    const missing=[...new Set(order.items.map(item=>item.skuId))].filter(id=>!state.products.some(product=>product.id===id));
    const products=await Promise.all(missing.map(id=>request('GET','/api/skus/'+id).then(result=>result.data)));
    if(epoch!==state.epoch||sequence!==detailSequence||!dialog.open)return;
    rememberProducts(products);
    $('#detail-dialog-title').textContent='订单 SF-'+order.id;
    const facts=node('dl',undefined,'detail-facts');
    for(const [label,value] of [['订单状态',orderBadge(order.status)],['确认截止时间',time(order.expiresAt)],...(scope==='fulfillment'?[['所属买家','买家 #'+order.buyerId]]:[]),...(order.cancelReason?[['取消原因',order.cancelReason==='EXPIRED'?'超过确认期限':'买家主动取消']]:[])]){
      const wrap=node('div');wrap.append(node('dt',label));const dd=node('dd');dd.append(value instanceof Node?value:document.createTextNode(value));wrap.append(dd);facts.append(wrap);
    }
    const table=node('table'),head=node('tr');['商品 / 编码','数量','单价','小计'].forEach(title=>head.append(node('th',title)));
    const thead=node('thead');thead.append(head);const body=node('tbody');
    for(const item of order.items){
      const row=node('tr'),product=state.products.find(p=>p.id===item.skuId);
      cell(row,productCell(product||{id:item.skuId},product?.code));cell(row,String(item.quantity),'number');cell(row,money(item.unitPrice),'number');cell(row,money(Number(item.unitPrice)*item.quantity),'number');body.append(row);
    }
    table.append(thead,body);const scroll=node('div',undefined,'table-scroll');scroll.append(table);
    const total=node('div',undefined,'detail-total');total.append(node('span','服务端订单金额'),node('strong',money(order.total)));
    const actions=node('div',undefined,'dialog-actions');for(const key of orderActions(order.status,scope==='fulfillment'?'WAREHOUSE':'BUYER'))actions.append(button(commandLabels[key],()=>openOrderCommand(order,key,scope),'button '+(key==='cancel'?'secondary':'primary')));
    $('#order-detail-content').replaceChildren(facts,renderOrderTimeline(order),scroll,total,node('p','待确认订单超过确认期限后，将自动取消并释放预留库存。确认是模拟业务操作，不涉及支付。','data-note'),actions);
  }catch(error){if(!error.stale && epoch===state.epoch && sequence===detailSequence && dialog.open)$('#order-detail-content').replaceChildren(node('p',failure(error),'form-error'));}
}
const commandLabels={confirm:'确认订单',cancel:'取消订单',ship:'确认发货',complete:'确认收货'};
function openOrderCommand(order,key,scope){
  if($('#order-command-form').dataset.busy==='true'){toast('正在处理上一笔操作，请稍候。');return;}
  state.pendingCommand={id:order.id,key,scope};message('#command-error');$('#command-title').textContent=commandLabels[key];
  const descriptions={confirm:'确认后将交由仓管发货，当前版本不支持确认后的取消。此操作不涉及支付。',cancel:'取消后会释放这笔订单预留的库存。',ship:'发货后会扣减实存量和预留量，请核对实际出库情况。',complete:'确认已收到商品，订单将变为已完成。'};
  $('#command-description').textContent='订单 SF-'+order.id+' · '+descriptions[key];$('#command-submit').textContent=commandLabels[key];$('#order-command-dialog').showModal();
}
$('#order-command-form').addEventListener('submit',event=>{
  event.preventDefault();return withForm(event.target,'#command-error',async()=>{
    const attempt=state.pendingCommand;if(!attempt)throw new Error('请重新选择订单操作。');
    const base=attempt.scope==='fulfillment'?'/api/fulfillment/orders/':'/api/orders/';
    await request('POST',base+attempt.id+'/'+attempt.key);$('#order-command-dialog').close();state.pendingCommand=null;toast(commandLabels[attempt.key]+'成功。');
    await refreshData();if($('#detail-dialog').open&&state.detailOrderId===attempt.id)await showOrder(attempt.id,attempt.scope);
  });
});
$('#show-register').addEventListener('click',()=>{setAuthForm(true);$('#register-user').focus();});
$('#show-login').addEventListener('click',()=>{setAuthForm(false);$('#login-user').focus();});
$('#register-form').addEventListener('submit',event=>{
  event.preventDefault();return withForm(event.target,'#register-error',async()=>{
    const username=$('#register-user').value.trim(),password=$('#register-password').value;
    if(password!==$('#register-confirm').value)throw new Error('两次输入的密码不一致。');
    await ensureCsrf(true);await request('POST','/api/auth/register',{username,password});
    $('#login-user').value=username;$('#register-password').value='';$('#register-confirm').value='';setAuthForm(false);toast('注册成功，请使用新账号登录。');$('#login-password').focus();
  });
});
$('#login-form').addEventListener('submit',event=>{
  event.preventDefault();return withForm(event.target,'#login-error',async()=>{
    const username=$('#login-user').value.trim(),password=$('#login-password').value;
    await ensureCsrf(true);const {data:me}=await request('POST','/api/auth/login',new URLSearchParams({username,password}));
    state.csrf='';await ensureCsrf(true);await activateSession(me);
  });
});
$('#sign-out').addEventListener('click',async()=>{
  const control=$('#sign-out');if(control.disabled)return;control.disabled=true;
  try{await ensureCsrf(true);await request('POST','/api/auth/logout');clearSession();}
  catch(error){if(!error.stale)toast('退出未完成：'+failure(error),true);}
  finally{control.disabled=false;}
});
window.addEventListener('DOMContentLoaded',restoreSession,{once:true});

$('#refresh-data').addEventListener('click',()=>refreshData());
window.addEventListener('hashchange',showPage);
$('#menu-toggle').addEventListener('click',()=>{const open=!$('#sidebar').classList.contains('open');$('#sidebar').classList.toggle('open',open);$('#sidebar-backdrop').hidden=!open;$('#menu-toggle').setAttribute('aria-expanded',String(open));});
$('#sidebar-backdrop').addEventListener('click',()=>{closeMenu();$('#menu-toggle').focus();});
$('.skip-link').addEventListener('click',event=>{event.preventDefault();$('#main-content').focus();});
document.addEventListener('keydown',event=>{if(event.key==='Escape'&&$('#sidebar').classList.contains('open')){closeMenu();$('#menu-toggle').focus();}});
$('#open-help').addEventListener('click',()=>$('#help-dialog').showModal());
all('[data-close]').forEach(item=>item.addEventListener('click',()=>$('#'+item.dataset.close).close()));
all('[data-filter]').forEach(input=>input.addEventListener('input',()=>{const kind=input.dataset.filter;state.queries[kind]=input.value;state.lists[kind].sequence++;clearTimeout(searchTimers.get(kind));searchTimers.set(kind,setTimeout(()=>loadMainList(kind,true),250));}));
$('#inventory-status').addEventListener('change',event=>{state.stockFilter=event.target.value;loadMainList('inventory',true);});
$('#products-status').addEventListener('change',event=>{state.productStatus=event.target.value;loadMainList('products',true);});
for(const kind of ['orders','fulfillment'])$('#'+kind+'-status').addEventListener('change',event=>{state[kind+'Status']=event.target.value;loadMainList(kind,true);});
$('#sku-form').addEventListener('submit',event=>{
  event.preventDefault();return withForm(event.target,'#sku-error',async()=>{
    const data=new FormData(event.target);priceToCents(data.get('price'));
    await request('POST','/api/skus',{code:data.get('code').trim(),name:data.get('name').trim(),price:Number(data.get('price'))});
    $('#sku-dialog').close();toast('商品已创建，可以继续为商品入库。');await refreshData();
  });
});
$('#receipt-reset').addEventListener('click',()=>resetReceipt());
$('#receipt-form').addEventListener('submit',event=>{
  event.preventDefault();return withForm(event.target,'#receipt-error',async()=>{
    if(!state.receiptAttempt){
      const data=new FormData(event.target),skuId=Number(data.get('skuId'));
      if(!Number.isSafeInteger(skuId)||skuId<1)throw new Error('请选择有效商品。');
      state.receiptAttempt={skuId,quantity:parseQuantity(data.get('quantity'),1000000),operationKey:data.get('operationKey')};
    }
    $('#receipt-search-trigger').disabled=true;const {skuId,...payload}=state.receiptAttempt;$('#receipt-product').disabled=true;$('#receipt-form input[name=quantity]').disabled=true;$('#receipt-reset').hidden=false;
    const {data:stock}=await request('POST','/api/inventory/'+skuId+'/receipts',payload);
    state.receiptConfirmed=true;
    message('#receipt-success','本次入库已确认：实存 '+stock.onHand+' 件，可用 '+stock.available+' 件。重试本次操作不会重复增加库存。');
    $('#receipt-submit').textContent='重试本次入库';toast('入库结果已确认。');await refreshData();if($('#product-dialog').open&&state.productDetail?.id===skuId)await openProduct(skuId);
  });
});
$('#cart-lines').addEventListener('input',updateCart);
$('#cart-lines').addEventListener('change',updateCart);
$('#cart-lines').addEventListener('click',event=>{
  if(event.target.closest('.remove-line')){event.target.closest('.cart-line').remove();updateCart();}
});
$('#add-cart-line').addEventListener('click',()=>appendCartLine());
$('#new-order-reset').addEventListener('click',resetOrder);
$('#order-form').addEventListener('submit',event=>{
  event.preventDefault();return withForm(event.target,'#cart-error',async()=>{
    if(!state.orderAttempt)state.orderAttempt={requestKey:$('#order-key').value,items:cartPreview(cartRows(),state.products).items};
    $('#cart-fieldset').disabled=true;$('#submit-order').textContent='重试本次下单';
    const {status,data:order}=await request('POST','/api/orders',state.orderAttempt);
    state.lastOrder=order;
    $('#order-success').replaceChildren(node('strong',(status===201?'订单已创建':'已返回原订单')+' · SF-'+order.id),node('div','服务端金额 '+money(order.total)),button('查看订单详情',()=>showOrder(order.id)));
    $('#order-success').hidden=false;message('#cart-error');toast(status===201?'下单成功，库存已预留。':'已返回原订单，库存没有重复预留。');await refreshData();
  });
});


async function loadInventoryReport(kind,reset=true){
  if(!warehouse())return;
  const report=state.reports[kind],epoch=state.epoch,sequence=++report.sequence;
  if(reset){
    report.cursor=null;report.history=[];
    report.query=$('#'+kind+'-search').value.trim();
    report.type=kind==='ledger'?$('#ledger-type').value:'';

  }
  report.loading=true;report.error='';renderInventoryReport(kind);
  const params=new URLSearchParams({limit:'20',q:report.query});
  if(report.cursor!==null)params.set('beforeId',String(report.cursor));
  if(kind==='ledger'&&report.type)params.set('type',report.type);

  try{
    const {data}=await request('GET','/api/inventory/'+kind+'?'+params);
    if(epoch!==state.epoch||report!==state.reports[kind]||sequence!==report.sequence)return;
    report.items=data.items;report.nextCursor=data.nextCursor;report.total=data.total??data.items.length;
  }catch(error){
    if(error.stale||epoch!==state.epoch||report!==state.reports[kind]||sequence!==report.sequence)return;
    report.error=failure(error);
  }finally{
    if(epoch===state.epoch&&report===state.reports[kind]&&sequence===report.sequence){report.loading=false;renderInventoryReport(kind);}
  }
}
function renderInventoryReport(kind){
  const report=state.reports[kind],body=$('#'+kind+'-body');body.replaceChildren();
  $('#'+kind+'-loading').hidden=!report.loading;message('#'+kind+'-error',report.error);
  $('#'+kind+'-total').textContent=report.loading||report.error?'—':String(report.total);
  $('#'+kind+'-empty').hidden=true;
  const types={INBOUND:['入库','success'],RESERVE:['订单预留','pending'],RELEASE:['取消释放','neutral'],SHIP:['发货出库','success'],ADJUST_IN:['盘盈调整','success'],ADJUST_OUT:['盘亏调整','pending']};
  const delta=value=>node('span',value>0?'+'+value:String(value),'delta '+(value>0?'positive':value<0?'negative':'neutral'));
  for(const item of report.items){
    const row=node('tr');
    if(kind==='ledger'){
      cell(row,time(item.createdAt),'code');cell(row,productCell(item,item.code));
      const [label,tone]=types[item.operationType]||[item.operationType,'neutral'];cell(row,badge(label,tone));
      cell(row,delta(item.onHandDelta),'number');cell(row,delta(item.reservedDelta),'number');
      const reference=item.orderId?button('SF-'+item.orderId,()=>showOrder(item.orderId,'fulfillment')):item.stocktakeId?button('ST-'+item.stocktakeId,()=>openStocktake(item.stocktakeId)):node('span','—');if(reference.tagName!=='SPAN')reference.disabled=report.loading||!!report.error;cell(row,reference);
      cell(row,item.actorId===0?'系统任务':'用户 #'+item.actorId,'code');
    }else{
      cell(row,productCell(item,item.code));cell(row,String(item.onHand),'number');cell(row,String(item.reserved),'number');cell(row,String(item.available),'number');
      cell(row,String(item.safetyStock),'number');cell(row,node('strong','+'+item.shortage,'stock-shortage'),'number');
      const actions=node('div',undefined,'cell-actions');const policy=button('调整规则',()=>openStockPolicy(item)),receipt=button('补货入库',()=>openAlertReceipt(item),'button secondary compact');policy.disabled=receipt.disabled=report.loading||!!report.error;actions.append(policy,receipt);cell(row,actions,'right');
    }
    body.append(row);
  }
  if(!report.loading&&!report.error&&!report.items.length)empty('#'+kind+'-empty',kind==='ledger'?'暂无匹配流水':'当前没有库存预警',kind==='ledger'?'调整搜索条件，或完成入库和订单操作后再查看。':'在售商品的可用量均未低于各自安全线，或没有匹配的搜索结果。');
  const previous=button('上一页',()=>{report.cursor=report.history.pop()??null;loadInventoryReport(kind,false);},'button secondary');
  const next=button('下一页',()=>{report.history.push(report.cursor);report.cursor=report.nextCursor;loadInventoryReport(kind,false);},'button secondary');
  previous.disabled=report.loading||!!report.error||report.history.length===0;next.disabled=report.loading||report.error!==''||report.nextCursor===null;
  if(report.error)$('#'+kind+'-error').append(button('重试查询',()=>loadInventoryReport(kind,false)));
  $('#'+kind+'-pagination').replaceChildren(node('span',kind==='ledger'?'每页 20 条 · 按最新记录排序':'共 '+(report.loading||report.error?'—':report.total)+' 个商品 · 每页 20 条'),previous,node('span','第 '+(report.history.length+1)+' 页'),next);
}
for(const kind of ['ledger','alerts'])$('#'+kind+'-filters').addEventListener('submit',event=>{event.preventDefault();loadInventoryReport(kind);});
$('#inventory-insight').addEventListener('click',()=>navigate('alerts'));
$('#hero-orders').addEventListener('click',()=>navigate('orders'));

async function openProduct(id){
  const epoch=state.epoch,sequence=++productSequence,dialog=$('#product-dialog');state.productDetail=null;
  $('#product-detail-title').textContent='商品详情';$('#product-detail-content').replaceChildren(node('p','正在加载商品…','muted'));
  if(!dialog.open)dialog.showModal();
  try{
    const [{data:product},stock]=await Promise.all([request('GET','/api/skus/'+id),warehouse()?request('GET','/api/inventory/'+id):Promise.resolve(null)]);
    if(epoch!==state.epoch||sequence!==productSequence||!dialog.open)return;
    rememberProducts([product]);state.productDetail={id,product,stock:stock?.data,entries:[],nextCursor:null,ledgerLoading:false,ledgerError:''};
    $('#product-detail-title').textContent=product.name;renderProductDetail();if(warehouse())await loadProductLedger();
  }catch(error){if(!error.stale&&epoch===state.epoch&&sequence===productSequence&&dialog.open)$('#product-detail-content').replaceChildren(node('p',failure(error),'form-error'),button('重新加载',()=>openProduct(id),'button secondary'));}
}
function renderProductDetail(){
  const detail=state.productDetail;if(!detail)return;
  const {product,stock}=detail,facts=node('dl',undefined,'product-facts');
  for(const [label,value]of [['SKU 编码',product.code],['销售单价',money(product.price)],['销售状态',product.active?'在售':'停用']]){const wrap=node('div');wrap.append(node('dt',label),node('dd',value));facts.append(wrap);}
  const actions=node('div',undefined,'product-actions');
  if(warehouse())actions.append(button('编辑商品',()=>openProductEditor(product),'button secondary'),button('建立盘点',()=>openStocktakeCreate(product),'button secondary'),button('商品入库',()=>openAlertReceipt({...product,skuId:product.id}),'button primary'));
  else if(product.active)actions.append(button('加入订单',()=>{$('#product-dialog').close();pickProduct(product.id);},'button primary'));
  const content=$('#product-detail-content');content.replaceChildren(facts,actions);
  if(stock){
    const section=node('section',undefined,'drawer-section'),metrics=node('div',undefined,'product-stock');
    section.append(node('h3','当前库存'));
    for(const [label,value]of [['实存量',stock.onHand],['已预留',stock.reserved],['可用量',stock.available]]){const metric=node('div');metric.append(node('small',label),node('strong',String(value)));metrics.append(metric);}
    section.append(metrics,node('p','可用量 = 实存量 − 已预留量','fine'));
    const policy=node('div',undefined,'stock-policy-card'),policyCopy=node('div');policyCopy.append(node('strong','安全库存 '+stock.safetyStock+' 件'),node('p',!product.active?'商品已停用，不计入库存预警。':stock.available<stock.safetyStock?'低于安全线，建议补货 '+(stock.safetyStock-stock.available)+' 件。':'当前可用库存达到安全线。','fine'));
    policy.append(policyCopy,button('调整规则',()=>openStockPolicy({...stock,name:product.name,code:product.code}),'button secondary compact'));section.append(policy);content.append(section);
    const journal=node('section',undefined,'drawer-section');journal.append(node('h3','库存流水'));const entries=node('div',undefined,'product-ledger');entries.id='product-ledger';journal.append(entries);content.append(journal);renderProductLedger();
  }
}
async function loadProductLedger(more=false){
  const detail=state.productDetail;if(!detail||!warehouse()||detail.ledgerLoading)return;
  const epoch=state.epoch,sequence=productSequence,cursor=more?detail.nextCursor:null;
  detail.ledgerLoading=true;detail.ledgerError='';renderProductLedger();
  const params=new URLSearchParams({skuId:String(detail.id),limit:'10'});if(cursor!==null)params.set('beforeId',String(cursor));
  try{
    const {data}=await request('GET','/api/inventory/ledger?'+params);
    if(epoch!==state.epoch||sequence!==productSequence||detail!==state.productDetail||!$('#product-dialog').open)return;
    detail.entries=more?[...detail.entries,...data.items]:data.items;detail.nextCursor=data.nextCursor;
  }catch(error){if(!error.stale&&epoch===state.epoch&&sequence===productSequence&&detail===state.productDetail)detail.ledgerError=failure(error);}
  finally{if(epoch===state.epoch&&sequence===productSequence&&detail===state.productDetail&&$('#product-dialog').open){detail.ledgerLoading=false;renderProductLedger();}}
}
function renderProductLedger(){
  const detail=state.productDetail,container=$('#product-ledger');if(!detail||!container)return;container.replaceChildren();
  const labels={INBOUND:'商品入库',RESERVE:'订单预留',RELEASE:'取消释放',SHIP:'发货出库',ADJUST_IN:'盘盈调整',ADJUST_OUT:'盘亏调整'};
  const delta=value=>value>0?'+'+value:String(value);
  for(const item of detail.entries){
    const row=node('div',undefined,'product-ledger-row'),main=node('div'),quantities=node('div');main.append(node('strong',labels[item.operationType]||item.operationType));
    const meta=node('p',time(item.createdAt)+' · 用户 #'+item.actorId,'ledger-meta');if(item.orderId)meta.append(button('SF-'+item.orderId,()=>showOrder(item.orderId,'fulfillment')));if(item.stocktakeId)meta.append(button('ST-'+item.stocktakeId,()=>openStocktake(item.stocktakeId)));main.append(meta);
    quantities.append(node('span','实存 '+delta(item.onHandDelta),'ledger-quantity'),node('small','预留 '+delta(item.reservedDelta),'ledger-balance'));row.append(main,quantities);container.append(row);
  }
  if(detail.ledgerError)container.append(node('p',detail.ledgerError,'form-error'),button('重试',()=>loadProductLedger(detail.entries.length>0),'button secondary'));
  else if(detail.ledgerLoading)container.append(node('p','正在加载流水…','fine'));
  else if(!detail.entries.length)container.append(node('p','暂无库存变动记录。','muted'));
  else if(detail.nextCursor!==null)container.append(button('加载更多流水',()=>loadProductLedger(true),'button secondary'));
}
function openProductEditor(product){
  if(!warehouse()||$('#product-edit-form').dataset.busy==='true')return;
  state.editingProduct=product;$('#product-edit-code').textContent='SKU '+product.code+' · 编码保持不变';
  $('#product-edit-name').value=product.name;$('#product-edit-price').value=String(product.price);$('#product-edit-active').value=String(product.active);message('#product-edit-error');
  if(!$('#product-edit-dialog').open)$('#product-edit-dialog').showModal();
}
$('#product-edit-form').addEventListener('submit',event=>{
  event.preventDefault();return withForm(event.target,'#product-edit-error',async()=>{
    const product=state.editingProduct;if(!product)throw new Error('请重新选择商品。');
    const name=$('#product-edit-name').value.trim(),price=$('#product-edit-price').value;
    if(!name)throw new Error('商品名称不能为空。');priceToCents(price);
    const {data}=await request('PUT','/api/skus/'+product.id,{name,price:Number(price),active:$('#product-edit-active').value==='true'});
    rememberProducts([data]);$('#product-edit-dialog').close();toast('商品信息已更新。');
    await refreshData();if($('#product-dialog').open&&state.productDetail?.id===product.id)await openProduct(product.id);
  });
});
async function openProductPicker(purpose,select=null){
  if(purpose==='receipt'&&($('#receipt-form').dataset.busy==='true'||state.receiptAttempt))return;
  if(purpose==='cart'&&state.orderAttempt)return;
  state.picker={purpose,select,page:1,query:'',total:0,totalPages:1};$('#picker-search').value='';$('#picker-title').textContent=purpose==='receipt'?'选择入库商品':purpose==='stocktake'?'选择盘点商品':'选择订单商品';
  if(!$('#picker-dialog').open)$('#picker-dialog').showModal();await loadPicker();
}
async function loadPicker(reset=false){
  const picker=state.picker;if(!picker)return;const sequence=++pickerSequence,epoch=state.epoch;if(reset){picker.page=1;picker.query=$('#picker-search').value.trim();}
  $('#picker-results').replaceChildren(node('p','正在查询全部商品…','muted'));message('#picker-note');$('#picker-pagination').replaceChildren();
  const params=new URLSearchParams({page:String(picker.page),size:'8',q:picker.query,status:picker.purpose==='cart'?'active':'all'});
  try{
    const {data}=await request('GET','/api/skus/page?'+params);
    if(epoch!==state.epoch||picker!==state.picker||sequence!==pickerSequence||!$('#picker-dialog').open)return;
    picker.page=data.page;picker.total=data.total;picker.totalPages=data.totalPages;
    const results=$('#picker-results');results.replaceChildren();
    for(const product of data.items){const choice=button('',()=>{
      if(picker!==state.picker||!$('#picker-dialog').open)return;
      if(picker.purpose==='cart'&&(state.orderAttempt||$('#cart-fieldset').disabled))return;
      if(picker.purpose==='receipt'&&(state.receiptAttempt||$('#receipt-form').dataset.busy==='true'))return;
      rememberProducts([product]);
      if(picker.purpose==='cart'){productOptions(picker.select,product.id,true);updateCart();}
      else if(picker.purpose==='stocktake'){if(state.stocktakeAttempt||$('#stocktake-form').dataset.busy==='true')return;productOptions($('#stocktake-product'),product.id);}
      else{productOptions($('#receipt-product'),product.id);}
      $('#picker-dialog').close();state.picker=null;
    },'picker-result');const main=node('span');main.append(node('strong',product.name),node('small',product.code));choice.append(main,node('span',money(product.price)+(product.active?'':' · 停用'),'picker-result-meta'));results.append(choice);}
    if(!data.items.length)results.append(node('p','没有匹配的商品，换个关键词试试。','muted'));
    const previous=button('上一页',()=>{picker.page--;loadPicker();},'button secondary'),next=button('下一页',()=>{picker.page++;loadPicker();},'button secondary');previous.disabled=data.page<=1;next.disabled=data.page>=data.totalPages;
    $('#picker-pagination').replaceChildren(node('span','共 '+data.total+' 件 · '+data.page+' / '+data.totalPages),previous,next);
  }catch(error){if(!error.stale&&epoch===state.epoch&&picker===state.picker&&sequence===pickerSequence){message('#picker-note',failure(error));$('#picker-results').replaceChildren(button('重新加载',()=>loadPicker(),'button secondary'));}}
}
$('#picker-form').addEventListener('submit',event=>{event.preventDefault();loadPicker(true);});
$('#receipt-search-trigger').addEventListener('click',()=>openProductPicker('receipt'));

function renderOrderTimeline(order){
  const section=node('section',undefined,'order-progress');section.append(node('h3','履约进度'));
  const list=node('ol',undefined,'order-timeline');
  for(const step of orderTimeline(order)){
    const item=node('li',undefined,(step.done?'done':'upcoming')+(step.current?' current':''));if(step.current)item.setAttribute('aria-current','step');
    const copy=node('div');copy.append(node('strong',step.label),node('span',step.at?new Intl.DateTimeFormat('zh-CN',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(new Date(step.at)):step.done?'时间未记录（历史订单）':'等待处理','timeline-time'));
    if(step.key==='cancelled')copy.append(node('small','预留库存已释放','fine'));
    item.append(node('span',step.done?'✓':'','timeline-dot'),copy);list.append(item);
  }
  section.append(list);return section;
}
function openStockPolicy(item){
  if(!warehouse()||$('#policy-form').dataset.busy==='true')return;
  state.stockPolicy={...item};$('#policy-product').textContent=item.name+' · '+item.code;$('#policy-safety-stock').value=String(item.safetyStock??10);
  $('#policy-available').textContent='当前可用 '+item.available+' 件';message('#policy-error');if(!$('#policy-dialog').open)$('#policy-dialog').showModal();
}
$('#policy-form').addEventListener('submit',event=>{
  event.preventDefault();return withForm(event.target,'#policy-error',async()=>{
    const item=state.stockPolicy;if(!item)throw new Error('请重新选择商品。');const safetyStock=parseQuantity($('#policy-safety-stock').value,1000000);
    await request('PUT','/api/inventory/'+item.skuId+'/policy',{safetyStock});$('#policy-dialog').close();toast('安全库存已保存。');
    await refreshData();if($('#product-dialog').open&&state.productDetail?.id===item.skuId)await openProduct(item.skuId);
  });
});

function newOperations(){return Object.fromEntries(['stocktakes','reconciliation','audit'].map(kind=>[kind,{items:[],page:1,size:8,total:0,totalPages:1,loading:false,error:'',sequence:0}]));}
const operationTitles={SKU_CREATE:'创建商品',SKU_UPDATE:'编辑商品',STOCK_POLICY:'调整安全库存',STOCKTAKE_CREATE:'建立盘点',STOCKTAKE_CONFIRM:'确认盘点',STOCKTAKE_CANCEL:'取消盘点'};
const countStatuses={DRAFT:'待确认',CONFIRMED:'已确认',CANCELLED:'已取消'};
let stocktakeSequence=0;
function copyIdentifier(value){return button(value,async()=>{try{await navigator.clipboard.writeText(value);toast('已复制 '+value);}catch{toast('复制失败，请手动选择编号复制。',true);}},'copy-identifier');}
function resetList(kind){
  state.queries[kind]='';$('#'+kind+'-search').value='';$('#'+kind+'-status').value='all';
  if(kind==='products')state.productStatus='all';else if(kind==='inventory')state.stockFilter='all';else state[kind+'Status']='all';
  loadMainList(kind,true);
}
function renderTasksAndTrend(){
  const summary=state.dashboard||{},tasks=warehouse()?[
    ['待发货',summary.confirmedCount,'处理已确认订单',()=>{state.fulfillmentStatus='CONFIRMED';$('#fulfillment-status').value='CONFIRMED';navigate('fulfillment');}],
    ['库存预警',summary.lowStockCount,'查看补货缺口',()=>navigate('alerts')],
    ['库存核对','只读','核对订单与流水',()=>navigate('reconciliation')]
  ]:[['待确认',summary.pendingCount,'确认我的订单',()=>{state.ordersStatus='PENDING_CONFIRM';$('#orders-status').value='PENDING_CONFIRM';navigate('orders');}],['待收货',summary.shippedCount,'查看已发货订单',()=>{state.ordersStatus='SHIPPED';$('#orders-status').value='SHIPPED';navigate('orders');}]];
  $('#overview-tasks').replaceChildren(...tasks.map(([label,count,note,action])=>{const card=button('',action,'task-card');card.disabled=!state.dashboard;card.append(node('span',label),node('strong',count??'—'),node('small',note+' ↗'));return card;}));
  const days=state.trends||[],maximum=Math.max(1,...days.flatMap(day=>warehouse()?[day.inbound,day.outbound,day.orders]:[day.orders]));
  $('#trend-subtitle').textContent=warehouse()?'入库件数 · 出库件数 · 新增订单数':'本人新增订单数';
  $('#trend-chart').replaceChildren(...days.map(day=>{
    const column=node('div',undefined,'trend-day'),bars=node('div',undefined,'trend-bars');
    for(const [key,label]of warehouse()?[['inbound','入库'],['outbound','出库'],['orders','订单']]:[['orders','订单']]){
      const value=Number(day[key])||0,bar=node('span',undefined,'trend-bar '+key);bar.setAttribute('style','height:'+(value/maximum*100)+'%');bar.setAttribute('aria-label',day.day+' '+label+' '+value);bar.title=day.day+' '+label+' '+value;bars.append(bar);
    }
    const totals=warehouse()?day.inbound+' / '+day.outbound+' / '+day.orders:String(day.orders);column.append(node('small',totals,'trend-count'),bars,node('span',day.day.slice(5).replace('-','/')));return column;
  }));
  message('#trend-note',state.trendsError?'趋势更新失败，保留上次结果：'+state.trendsError:days.length?'统计最近七个自然日；盘点调整不计入收发货。':'暂无趋势数据');
}
async function loadOperations(kind,reset=false){
  if(!warehouse())return;const list=state.operations[kind],epoch=state.epoch,sequence=++list.sequence;if(reset)list.page=1;
  const params=new URLSearchParams({q:$('#'+kind+'-search').value.trim(),page:String(list.page),size:String(list.size)});
  if(kind==='stocktakes')params.set('status',$('#stocktakes-status').value);
  if(kind==='audit')params.set('action',$('#audit-action').value);
  if(kind==='reconciliation')params.set('mismatchOnly',String($('#reconciliation-only').checked));
  list.loading=true;list.error='';renderOperations(kind);
  try{const {data}=await request('GET',(kind==='reconciliation'?'/api/inventory/reconciliation':'/api/'+kind)+'?'+params);
    if(epoch!==state.epoch||list!==state.operations[kind]||sequence!==list.sequence)return;Object.assign(list,data);
  }catch(error){if(error.stale||epoch!==state.epoch||list!==state.operations[kind]||sequence!==list.sequence)return;list.error=failure(error);}
  finally{if(epoch===state.epoch&&list===state.operations[kind]&&sequence===list.sequence){list.loading=false;renderOperations(kind);}}
}
function renderOperations(kind){
  const list=state.operations[kind],body=$('#'+kind+'-body'),unavailable=list.loading||!!list.error;body.replaceChildren();
  const action=(label,handler)=>{const control=button(label,handler);control.disabled=unavailable;return control;};
  for(const item of list.items){const row=node('tr');
    if(kind==='stocktakes'){
      cell(row,copyIdentifier('ST-'+item.id));cell(row,productCell({name:item.skuName},item.skuCode));cell(row,String(item.originalOnHand),'number');cell(row,String(item.targetOnHand),'number');cell(row,badge(countStatuses[item.status],item.status==='DRAFT'?'pending':item.status==='CONFIRMED'?'success':'neutral'));cell(row,time(item.createdAt));cell(row,action('查看盘点',()=>openStocktake(item.id)),'right');
    }else if(kind==='reconciliation'){
      cell(row,productCell(item,item.code));cell(row,item.onHand+' / '+item.ledgerOnHand,'number');cell(row,item.reserved+' / '+item.ledgerReserved,'number');cell(row,String(item.orderReserved),'number');cell(row,badge(item.consistent?'一致':'存在差异',item.consistent?'success':'pending'));cell(row,action('商品流水',()=>openProduct(item.skuId)),'right');
    }else{
      cell(row,time(item.createdAt),'code');cell(row,item.username||'用户 #'+item.actorId);cell(row,operationTitles[item.action]||item.action);cell(row,item.entityLabel);cell(row,action('变更详情',()=>openAudit(item)),'right');
    }body.append(row);
  }
  body.classList.toggle('table-updating',list.loading);message('#'+kind+'-note',list.loading?'正在更新，保留上次结果…':list.error?'更新失败，保留上次结果：'+list.error:'');
  $('#'+kind+'-empty').hidden=!!list.items.length||list.loading||!!list.error;
  if(!list.items.length&&!list.loading&&!list.error)empty('#'+kind+'-empty',kind==='reconciliation'?'当前条件下没有差异':'暂无匹配记录',kind==='stocktakes'?'建立盘点单，记录实盘数量和原因。':'可以调整搜索与筛选条件后重试。');
  const previous=button('上一页',()=>{list.page--;loadOperations(kind);},'button secondary'),next=button('下一页',()=>{list.page++;loadOperations(kind);},'button secondary');previous.disabled=unavailable||list.page<=1;next.disabled=unavailable||list.page>=list.totalPages;
  const controls=node('div');controls.append(previous,node('span',list.page+' / '+list.totalPages),next);if(list.error)controls.append(button('重试',()=>loadOperations(kind)));
  $('#'+kind+'-pagination').replaceChildren(node('span','共 '+list.total+' 条'),controls);
  if(kind==='reconciliation')$('#reconciliation-summary').textContent='当前搜索范围 · 核对 '+(list.checkedCount??'—')+' 个商品 · 差异 '+(list.mismatchCount??'—')+' 个';
}
function openStocktakeCreate(product){
  if(!warehouse()||$('#stocktake-form').dataset.busy==='true')return;
  if(!state.stocktakeAttempt){$('#stocktake-form').reset();productOptions($('#stocktake-product'),product?.id||'');$('#stocktake-inputs').disabled=false;$('#stocktake-submit').textContent='建立盘点单';message('#stocktake-error');}
  if(!$('#stocktake-create-dialog').open)$('#stocktake-create-dialog').showModal();
}
async function openStocktake(id){
  if($('#stocktake-command-form').dataset.busy==='true')return;
  const epoch=state.epoch,sequence=++stocktakeSequence,dialog=$('#stocktake-detail-dialog');state.stocktakeDetail=null;$('#stocktake-detail-title').textContent='盘点 ST-'+id;$('#stocktake-detail-content').replaceChildren(node('p','正在读取盘点…','muted'));$('#stocktake-command-controls').hidden=true;message('#stocktake-command-error');if(!dialog.open)dialog.showModal();
  try{const {data}=await request('GET','/api/stocktakes/'+id);if(epoch!==state.epoch||sequence!==stocktakeSequence||!dialog.open)return;state.stocktakeDetail=data;renderStocktake(data);}
  catch(error){if(!error.stale&&epoch===state.epoch&&sequence===stocktakeSequence&&dialog.open)$('#stocktake-detail-content').replaceChildren(node('p',failure(error),'form-error'),button('重试',()=>openStocktake(id),'button secondary'));}
}
function renderStocktake(data){
  const facts=node('dl',undefined,'product-facts');for(const [label,value]of [['商品',data.skuName+' · '+data.skuCode],['盘点状态',countStatuses[data.status]],['建立时实存',data.originalOnHand],['建立时预留',data.originalReserved],['实盘数量',data.targetOnHand],['实际调整',data.adjustment==null?'待确认':(data.adjustment>0?'+':'')+data.adjustment],['盘点原因',data.reason],['建立时间',time(data.createdAt)],['确认时间',time(data.confirmedAt)]]){const line=node('div');line.append(node('dt',label),node('dd',String(value)));facts.append(line);}
  $('#stocktake-detail-content').replaceChildren(facts);$('#stocktake-command-controls').hidden=data.status!=='DRAFT';
}
function openAudit(item){
  const before=item.before||{},after=item.after||{},labels={code:'SKU 编码',name:'商品名称',price:'单价',active:'销售状态',safetyStock:'安全库存',status:'盘点状态',targetOnHand:'实盘数量',originalOnHand:'原实存量',originalReserved:'原预留量',reason:'盘点原因',adjustment:'调整数量',confirmedBy:'确认人编号',confirmedAt:'确认时间'};
  $('#audit-detail-title').textContent=operationTitles[item.action]||item.action;const table=node('table'),head=node('tr');['项目','变更前','变更后'].forEach(text=>head.append(node('th',text)));const thead=node('thead');thead.append(head);table.append(thead);const body=node('tbody');
  const format=(key,value)=>value==null?'—':key==='active'?(value?'在售':'停用'):key==='status'?(countStatuses[value]||value):key==='confirmedAt'?time(value):String(value);
  for(const [key,label]of Object.entries(labels)){if(!(key in before)&&!(key in after))continue;if(key in before&&key in after&&before[key]===after[key])continue;const row=node('tr');cell(row,label);cell(row,format(key,before[key]));cell(row,format(key,after[key]));body.append(row);}table.append(body);
  const wrap=node('div',undefined,'table-scroll');wrap.append(table);$('#audit-detail-content').replaceChildren(node('p',item.entityLabel,'muted'),node('p',(item.username||'用户 #'+item.actorId)+' · '+time(item.createdAt),'fine'),wrap,node('p','请求编号 '+item.requestId,'fine'));$('#audit-detail-dialog').showModal();
}
for(const kind of ['stocktakes','reconciliation','audit'])$('#'+kind+'-filters').addEventListener('submit',event=>{event.preventDefault();loadOperations(kind,true);});
$('#stocktake-search-trigger').addEventListener('click',()=>{if(!state.stocktakeAttempt&&$('#stocktake-form').dataset.busy!=='true')openProductPicker('stocktake');});
$('#stocktake-new-attempt').addEventListener('click',()=>{if($('#stocktake-form').dataset.busy==='true')return;if(state.stocktakeAttempt)toast('请先在盘点列表核对上一笔结果。');state.stocktakeAttempt=null;openStocktakeCreate();});
$('#stocktake-form').addEventListener('submit',event=>{event.preventDefault();return withForm(event.target,'#stocktake-error',async()=>{
  if(!state.stocktakeAttempt){const raw=$('#stocktake-target').value,target=Number(raw),skuId=Number($('#stocktake-product').value),reason=$('#stocktake-reason').value.trim();if(!/^\d+$/.test(raw)||!Number.isSafeInteger(target)||target<0||target>1000000000||!skuId||!reason)throw new Error('请选择商品，填写 0–10 亿的整数实盘数量和原因。');state.stocktakeAttempt={requestKey:crypto.randomUUID(),skuId,targetOnHand:target,reason};}
  $('#stocktake-inputs').disabled=true;$('#stocktake-submit').textContent='重试本次盘点';const {data}=await request('POST','/api/stocktakes',state.stocktakeAttempt);state.stocktakeAttempt=null;$('#stocktake-create-dialog').close();toast('盘点单已建立，确认后才会调整库存。');await loadOperations('stocktakes',true);await openStocktake(data.id);
});});
$('#stocktake-command-form').addEventListener('submit',event=>{event.preventDefault();const command=event.submitter?.value;return withForm(event.target,'#stocktake-command-error',async()=>{
  const detail=state.stocktakeDetail;if(!detail||!['confirm','cancel'].includes(command))throw new Error('请重新打开盘点单。');$('#stocktake-cancel').disabled=true;
  try{const {data}=await request('POST','/api/stocktakes/'+detail.id+'/'+command);state.stocktakeDetail=data;renderStocktake(data);toast(command==='confirm'?'盘点已确认，差异已记录。':'盘点已取消。');await Promise.all([refreshData(),loadOperations('stocktakes')]);}finally{if(state.stocktakeDetail===detail||state.stocktakeDetail?.id===detail.id)$('#stocktake-cancel').disabled=false;}
});});
