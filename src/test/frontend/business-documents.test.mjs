import test from 'node:test';
import assert from 'node:assert/strict';
import {createDocumentUI} from '../../main/resources/static/business-documents.mjs';
class Element{
 constructor(tag,text=''){this.tag=tag;this.children=[];this.listeners={};this.value='';this.open=false;this.ownText=text||'';this.disabled=false;}
 get textContent(){return this.ownText+this.children.map(item=>item.textContent).join('');}set textContent(text){this.ownText=String(text);this.children=[];}
 append(...items){for(const item of items){item.parentNode=this;this.children.push(item);}}replaceChildren(...items){this.children=[];this.ownText='';this.append(...items);}
 querySelectorAll(selector){return this.children.flatMap(child=>[...(child.tag===selector?[child]:[]),...child.querySelectorAll(selector)]);}
 addEventListener(type,handler){this.listeners[type]=handler;}setAttribute(){}focus(){}showModal(){this.open=true;}close(){this.open=false;this.listeners.close?.();}remove(){this.parentNode.children=this.parentNode.children.filter(item=>item!==this);this.parentNode=null;}
}
const deferred=()=>{let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no;});return {promise,resolve,reject};};
function setup(request){const elements=new Map(),body=new Element('body'),state={epoch:1,me:{id:1}};globalThis.document={body,getElementById(id){if(!elements.has(id))elements.set(id,new Element('section'));return elements.get(id);}};globalThis.Option=class extends Element{constructor(text,value){super('option',text);this.value=value;}};
 const node=(tag,text)=>new Element(tag,text),button=(text,action)=>{const el=node('button',text);el.listeners.click=action;return el;};const ui=createDocumentUI({state,request,warehouse:()=>true,buyer:()=>false,node,button,money:String,time:String,toast(){},pickProduct(){},refresh:async()=>{},onQuery(){}});return {ui,state,body,elements};
}
test('new document list ignores late previous query and retains disabled last rows on failure',async()=>{
 const responses=[];const {ui,elements}=setup(()=>{const pending=deferred();responses.push(pending);return pending.promise;});
 const first=ui.show('receipts',{q:'old',page:1,size:8}),second=ui.show('receipts',{q:'new',page:1,size:8});
 responses[1].resolve({data:{items:[{id:2,source:'新查询',status:'DRAFT'}],page:1,size:8,total:1,totalPages:1}});await second;responses[0].resolve({data:{items:[{id:1,source:'旧查询',status:'DRAFT'}],page:1,size:8,total:1,totalPages:1}});await first;
 assert.match(elements.get('view-receipts').textContent,/新查询/);assert.doesNotMatch(elements.get('view-receipts').textContent,/旧查询/);
 const failure=ui.show('receipts',{q:'bad',page:1,size:8});responses[2].reject(new Error('offline'));await failure;assert.match(elements.get('view-receipts').textContent,/offline/);assert.match(elements.get('view-receipts').textContent,/新查询/);assert.equal(elements.get('view-receipts').querySelectorAll('button').find(button=>button.textContent==='查看详情').disabled,true);
});
test('receipt form freezes uncertain request and retries same key, then rejects identity-stale success',async()=>{
 const calls=[],pending=deferred();let count=0;const {ui,state,body}=setup(async(method,path,payload)=>{if(method==='POST'){calls.push(payload);if(++count===1)throw new Error('offline');return pending.promise;}return {data:[]};});
 ui.createReceipt();const form=body.children[0].children[0],fields=form.children[2],source=fields.children[0].children[1],line=fields.children[1].children[0];source.value='供应商';line.children[0].value='12';line.children[2].value='5';
 await form.onsubmit({preventDefault(){}});assert.equal(fields.disabled,true);assert.match(form.textContent,/offline/);const retry=form.onsubmit({preventDefault(){}});assert.equal(calls.length,2);assert.equal(calls[0],calls[1]);
 state.epoch++;ui.reset();pending.resolve({data:{id:3}});await retry;assert.equal(body.children[0].open,false);assert.equal(body.children[1].open,false);
});
