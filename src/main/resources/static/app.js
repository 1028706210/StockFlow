import {parseQuantity,priceToCents,cartPreview,paginateRows,stockState,orderActions} from '/ui-model.mjs';

import {requestJson} from '/api-client.mjs';

const $ = selector => document.querySelector(selector);
const all = selector => [...document.querySelectorAll(selector)];
const money = value => new Intl.NumberFormat('zh-CN',{style:'currency',currency:'CNY'}).format(value);
const time = value => value ? new Intl.DateTimeFormat('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date(value)) : '—';
const pages = {
  overview:['工作概览','WORKSPACE OVERVIEW','从当前业务数据，了解库存与订单的状态。'],
  products:['商品管理','PRODUCT CATALOG','统一管理商品名称、编码与销售价格。'],
  inventory:['库存管理','INVENTORY CONTROL','查看实存与预留，为商品补充可用库存。'],
  fulfillment:['订单履约','ORDER FULFILLMENT','查询订单状态，为已确认的订单完成发货。'],
  orders:['我的订单','MY ORDERS','查看本人订单、服务端金额与商品明细。'],
  'new-order':['创建订单','CREATE AN ORDER','选择商品和数量，提交一笔新的订单。']
};
const state = {csrf:'',me:null,epoch:0,refreshId:0,loading:false,loaded:false,error:'',products:[],stocks:[],orders:[],fulfillment:[],pendingCommand:null,detailOrderId:null,
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
  state.epoch++;state.refreshId++;state.csrf='';state.me=null;state.products=[];state.stocks=[];state.orders=[];state.fulfillment=[];
  state.loaded=false;state.loading=false;state.error='';state.orderAttempt=null;state.lastOrder=null;state.receiptAttempt=null;state.receiptConfirmed=false;state.pendingCommand=null;state.detailOrderId=null;detailSequence++;
  all('form').forEach(form=>{form.dataset.operation='';form.dataset.busy='false';form.removeAttribute('aria-busy');const submit=form.querySelector('[type=submit]');if(submit)submit.disabled=false;});
  $('#receipt-reset').disabled=false;$('#new-order-reset').disabled=false;$('#sign-out').disabled=false;
  all('dialog[open]').forEach(dialog=>dialog.close());closeMenu();$('#workspace').hidden=true;$('#login-screen').hidden=false;$('#toast').hidden=true;
  $('#login-password').value='';$('#register-password').value='';$('#register-confirm').value='';$('#cart-lines').replaceChildren();setAuthForm(false);message('#login-error',note);$('#login-password').focus();
}
function setAuthForm(register){
  $('#login-form').hidden=register;$('#register-form').hidden=!register;$('#show-register').hidden=register;$('#show-login').hidden=!register;
  $('#auth-title').textContent=register?'注册买家账号':'进入工作台';$('#auth-description').textContent=register?'建立自己的账号，开始管理你的订单。':'登录账号，开始处理业务。';
  message('#login-error');message('#register-error');
}
async function activateSession(me){
  state.me=me;state.epoch++;state.pendingCommand=null;state.detailOrderId=null;
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
  if(!pages[page] || (['inventory','fulfillment'].includes(page)&&!warehouse()) || (['orders','new-order'].includes(page)&&!buyer()))page='overview';
  all('.view').forEach(view=>{view.hidden=view.id!=='view-'+page;});
  all('[data-nav]').forEach(item=>{item.classList.toggle('active',item.dataset.nav===page);if(item.dataset.nav===page)item.setAttribute('aria-current','page');else item.removeAttribute('aria-current');});
  $('#page-title').textContent=pages[page][0];$('#breadcrumb-page').textContent=pages[page][0];
  $('#page-kicker').textContent=pages[page][1];$('#page-description').textContent=pages[page][2];
  const action=$('#heading-action');action.hidden=false;
  if(page==='products'&&warehouse()){action.textContent='新增商品';action.onclick=openSku;}
  else if(page==='inventory'&&warehouse()){action.textContent='商品入库';action.onclick=()=>openReceipt();}
  else if((page==='orders'||page==='overview')&&buyer()){action.textContent='创建订单';action.onclick=()=>navigate('new-order');}
  else if(page==='overview'&&warehouse()){action.textContent='新增商品';action.onclick=openSku;}
  else action.hidden=true;
  closeMenu();
}
async function refreshData(){
  const epoch=state.epoch,refreshId=++state.refreshId;
  state.loading=true;$('#refresh-data').disabled=true;$('#loading-note').hidden=false;
  message('#load-error');$('#main-content').setAttribute('aria-busy','true');
  try {
    const [products,stocks,orders,fulfillment]=await Promise.all([
      request('GET','/api/skus'),warehouse()?request('GET','/api/inventory'):Promise.resolve({data:[]}),
      buyer()?request('GET','/api/orders'):Promise.resolve({data:[]}),
      warehouse()?request('GET','/api/fulfillment/orders'):Promise.resolve({data:[]})
    ]);
    if(epoch!==state.epoch || refreshId!==state.refreshId)return;
    state.products=products.data;state.stocks=stocks.data;state.orders=orders.data;state.fulfillment=fulfillment.data;state.loaded=true;state.error='';
    $('#updated-at').textContent='更新于 '+new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false});
    rebuildCartOptions();
  } catch(error) {
    if(error.stale || epoch!==state.epoch || refreshId!==state.refreshId)return;
    state.error=failure(error);state.loaded=false;state.products=[];state.stocks=[];state.orders=[];state.fulfillment=[];
    message('#load-error','数据加载失败：'+state.error);$('#updated-at').textContent='数据未同步';
  } finally {
    if(epoch===state.epoch && refreshId===state.refreshId){
      state.loading=false;$('#refresh-data').disabled=false;$('#loading-note').hidden=true;$('#main-content').removeAttribute('aria-busy');render();updateCart();
    }
  }
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
  if(!state.me)return;
  renderOverview();renderTable('products');renderTable('inventory');renderTable('orders');renderTable('fulfillment');
  $('#products-loaded').textContent='已加载 '+state.products.length+' 条 · 最近 100 条';
  $('#orders-loaded').textContent='已加载 '+state.orders.length+' 条 · 最近 100 条';
  $('#fulfillment-loaded').textContent='已加载 '+state.fulfillment.length+' 条 · 最近 100 条';
}
function renderOverview(){
  const valid=state.loaded&&!state.error;
  const metrics=warehouse()?[
    ['商品种类',state.products.length,'CATALOG','当前加载的商品数量',''],
    ['实存总量',state.stocks.reduce((s,r)=>s+r.onHand,0),'ON HAND','入库后的实物数量',''],
    ['已预留量',state.stocks.reduce((s,r)=>s+r.reserved,0),'RESERVED','待处理订单占用的数量','accent'],
    ['低库存商品',state.stocks.filter(r=>r.available<10).length,'ATTENTION','可用量低于 10 件，含缺货','warning']
  ]:[
    ['在售商品',state.products.filter(p=>p.active).length,'CATALOG','当前可选择的商品种类',''],
    ['我的订单',state.orders.length,'MY ORDERS','当前加载的本人订单数量',''],
    ['待确认订单',state.orders.filter(o=>o.status==='PENDING_CONFIRM').length,'PENDING','已创建并预留库存的订单','accent'],
    ['订单总额',money(state.orders.reduce((sum,o)=>sum+Number(o.total),0)),'ORDER VALUE','当前加载订单的金额合计','']
  ];
  $('#metrics').replaceChildren(...metrics.map(([label,value,kicker,note,tone])=>{
    const item=node('div',undefined,'metric '+tone),heading=node('div',label,'metric-label');heading.append(node('span',kicker));
    item.append(heading,node('div',valid?String(value):'—','metric-value'),node('p',note,'metric-note'));return item;
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
      cell(row,productCell(product||{id:item.skuId},item.code));cell(row,String(item.available),'number');
      const status=stockState(item);cell(row,badge(status.label,status.tone));
    }else{cell(row,'SF-'+item.id,'code');cell(row,money(item.total),'number');cell(row,orderBadge(item.status));}
    $('#recent-body').append(row);
  }
  $('#recent-empty').hidden=rows.length>0;
  if(!rows.length)empty('#recent-empty',state.error?'数据暂不可用':warehouse()?'还没有库存记录':'还没有订单',state.error?'请检查服务连接后，点击刷新数据。':warehouse()?'先创建商品，再为商品完成第一笔入库。':'选择商品并提交后，可以在这里查看订单。');
  $('#quick-description').textContent=warehouse()?'让库存准备就绪，再交给买家下单。':'选好商品和数量，订单金额由服务端确认。';
  $('#quick-actions').replaceChildren(...(warehouse()?[
    button('新增商品',openSku,'button secondary'),button('为商品入库',()=>openReceipt(),'button primary'),button('处理订单履约',()=>navigate('fulfillment'),'button secondary')
  ]:[
    button('浏览商品',()=>navigate('products'),'button secondary'),button('创建第一笔 / 下一笔订单',()=>navigate('new-order'),'button primary')
  ]));
}
function tableRows(kind){
  if(kind==='products')return state.products;
  if(kind==='orders'||kind==='fulfillment')return state[kind].map(order=>({...order,displayId:'SF-'+order.id}))
    .filter(order=>state[kind+'Status']==='all'||order.status===state[kind+'Status']);
  return state.stocks.map(stock=>({...stock,name:state.products.find(p=>p.id===stock.skuId)?.name||'商品 #'+stock.skuId}))
    .filter(stock=>state.stockFilter==='all'||stockState(stock).key===state.stockFilter);
}
function renderTable(kind){
  const orderList=kind==='orders'||kind==='fulfillment',source=tableRows(kind),keys=orderList?['id','displayId','buyerId']:['name','code'];
  const data=paginateRows(source,state.queries[kind],state.page[kind],8,keys);state.page[kind]=data.page;
  const body=$('#'+kind+'-body');body.replaceChildren();
  for(const item of data.rows){
    const row=node('tr');
    if(kind==='products'){
      cell(row,productCell(item));cell(row,item.code,'code');cell(row,money(item.price),'number');cell(row,badge(item.active?'在售':'停用',item.active?'success':'neutral'));
      cell(row,button(warehouse()?'入库':'选购',()=>warehouse()?openReceipt(item.id):pickProduct(item.id)),'right');
    }else if(kind==='inventory'){
      cell(row,productCell({name:item.name},item.code));cell(row,String(item.onHand),'number');cell(row,String(item.reserved),'number');cell(row,node('strong',String(item.available)),'number');
      const status=stockState(item);cell(row,badge(status.label,status.tone));cell(row,button('入库',()=>openReceipt(item.skuId)),'right');
    }else{
      cell(row,'SF-'+item.id,'code');if(kind==='fulfillment')cell(row,'买家 #'+item.buyerId);
      const status=node('div');status.append(orderBadge(item.status));if(item.cancelReason)status.append(node('span',item.cancelReason==='EXPIRED'?'超时取消':'买家取消','subtext'));cell(row,status);
      cell(row,money(item.total),'number');cell(row,time(item.expiresAt));
      const actions=node('div',undefined,'cell-actions');actions.append(button('查看详情',()=>showOrder(item.id,kind)));
      for(const key of orderActions(item.status,kind==='fulfillment'?'WAREHOUSE':'BUYER'))actions.append(button(commandLabels[key],()=>openOrderCommand(item,key,kind)));
      cell(row,actions,'right');
    }
    body.append(row);
  }
  $('#'+kind+'-empty').hidden=data.rows.length>0;
  if(!data.rows.length){
    const filtered=!!state.queries[kind]||(kind==='inventory'&&state.stockFilter!=='all')||(orderList&&state[kind+'Status']!=='all');
    empty('#'+kind+'-empty',state.error?'加载失败':filtered?'没有匹配的记录':orderList?'还没有订单':kind==='inventory'?'还没有库存记录':'还没有商品',
      state.error?'请检查服务连接，再点击刷新数据。':filtered?'换个关键词或筛选条件试试。':kind==='orders'?'从选择商品开始，创建你的第一笔订单。':kind==='fulfillment'?'买家下单后，可以在这里查看并处理订单。':'创建商品并完成入库后，业务数据会显示在这里。',
      state.error?button('重新加载',refreshData,'button secondary'):!filtered&&kind==='orders'?button('创建订单',()=>navigate('new-order'),'button primary'):!filtered&&kind==='products'&&warehouse()?button('新增商品',openSku,'button primary'):null);
  }
  const pagination=$('#'+kind+'-pagination'),controls=node('div');
  const previous=button('上一页',()=>{state.page[kind]--;renderTable(kind);},'');previous.disabled=data.page<=1;
  const next=button('下一页',()=>{state.page[kind]++;renderTable(kind);},'');next.disabled=data.page>=data.totalPages;
  controls.append(previous,node('span',data.page+' / '+data.totalPages),next);pagination.replaceChildren(node('span','共 '+data.total+' 条 · 每页 8 条'),controls);
}

function productOptions(select,selected='',onlyActive=false){
  select.replaceChildren(new Option('请选择商品',''));
  for(const p of state.products.filter(p=>!onlyActive||p.active))select.append(new Option(p.name+' · '+p.code+(onlyActive?' · '+money(p.price):''),String(p.id)));
  select.value=String(selected);
}
function openSku(){message('#sku-error');$('#sku-form').reset();$('#sku-dialog').showModal();}
function openReceipt(id){
  if(!warehouse())return;
  if(!state.products.length){toast('请先创建商品，或刷新商品列表。',true);return;}
  if(!state.receiptAttempt || state.receiptConfirmed)resetReceipt(id);
  else if(id && id!==state.receiptAttempt.skuId)toast('上一笔入库尚未确认，请先重试原操作，或明确开始新一笔。',true);
  $('#receipt-dialog').showModal();
}
function resetReceipt(id){
  if($('#receipt-form').dataset.busy==='true')return;
  state.receiptAttempt=null;state.receiptConfirmed=false;$('#receipt-form').reset();productOptions($('#receipt-product'),id||state.products[0]?.id);
  $('#receipt-key').value=crypto.randomUUID();$('#receipt-product').disabled=false;$('#receipt-form input[name=quantity]').disabled=false;
  message('#receipt-error');message('#receipt-success');$('#receipt-submit').textContent='确认入库';$('#receipt-reset').hidden=true;
}
function cartRows(){return all('.cart-line').map(row=>({skuId:row.querySelector('.cart-product').value,quantity:row.querySelector('.cart-quantity').value}));}
function appendCartLine(id='',quantity=1){
  if(all('.cart-line').length>=10){toast('最多添加 10 行商品。',true);return;}
  const row=$('#cart-line-template').content.firstElementChild.cloneNode(true);
  productOptions(row.querySelector('select'),id,true);row.querySelector('input').value=quantity;$('#cart-lines').append(row);updateCart();
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
    if(blank)blank.querySelector('select').value=String(id);else appendCartLine(id);
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
    $('#order-detail-content').replaceChildren(facts,scroll,total,node('p','待确认订单超过确认期限后，将自动取消并释放预留库存。确认是模拟业务操作，不涉及支付。','data-note'),actions);
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
all('[data-filter]').forEach(input=>input.addEventListener('input',()=>{const kind=input.dataset.filter;state.queries[kind]=input.value;state.page[kind]=1;renderTable(kind);}));
$('#inventory-status').addEventListener('change',event=>{state.stockFilter=event.target.value;state.page.inventory=1;renderTable('inventory');});
for(const kind of ['orders','fulfillment'])$('#'+kind+'-status').addEventListener('change',event=>{state[kind+'Status']=event.target.value;state.page[kind]=1;renderTable(kind);});
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
    const {skuId,...payload}=state.receiptAttempt;$('#receipt-product').disabled=true;$('#receipt-form input[name=quantity]').disabled=true;$('#receipt-reset').hidden=false;
    const {data:stock}=await request('POST','/api/inventory/'+skuId+'/receipts',payload);
    state.receiptConfirmed=true;
    message('#receipt-success','本次入库已确认：实存 '+stock.onHand+' 件，可用 '+stock.available+' 件。重试本次操作不会重复增加库存。');
    $('#receipt-submit').textContent='重试本次入库';toast('入库结果已确认。');await refreshData();
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
