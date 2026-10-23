import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {parseQuantity,priceToCents,cartPreview,paginateRows,stockState} from '../../main/resources/static/ui-model.mjs';

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
  const elements=new Map();
  const get=selector=>{if(!elements.has(selector))elements.set(selector,new Element(selector.replace('#','')));return elements.get(selector);};
  get('#cart-line-template').content={firstElementChild:new Element('cart-template')};
  const document={addEventListener(){},querySelector:get,querySelectorAll:selector=>selector==='.cart-line'?get('#cart-lines').children:[],createElement:tag=>new Element(tag),createTextNode:text=>{const e=new Element();e.textContent=text;return e;}};
  const context={document,window:{addEventListener(){}},location:{hash:''},Node:Element,Option:class extends Element{constructor(text,value){super();this.textContent=text;this.value=value;}},Intl,Date,Map,Set,Number,String,Error,AbortSignal,crypto:{randomUUID:()=> 'new-key'},setTimeout:()=>0,clearTimeout(){},parseQuantity,priceToCents,cartPreview,paginateRows,stockState,requestJson,console};
  let source=readFileSync(new URL('../../main/resources/static/app.js',import.meta.url),'utf8').replace(/^import .*;\r?\n/gm,'');
  source+='\nglobalThis.probe={state,showOrder,withForm,openReceipt};';
  vm.runInNewContext(source,context);
  return {get,ui:context.probe};
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
