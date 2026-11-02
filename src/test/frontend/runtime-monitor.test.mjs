import test from 'node:test';
import assert from 'node:assert/strict';
import {createRuntimeMonitor,summarizeRuntime} from '../../main/resources/static/assets/ui/runtime-monitor.mjs';

const snapshot=()=>({observedAt:'2026-11-02T06:00:00Z',databaseStatus:'UP',expiryEnabled:true,expiryDelayMillis:30000,metrics:{process:{startedAt:'2026-11-02T05:00:00Z',uptimeSeconds:3600},requests:{count:20,durationNanos:40000000,statusClasses:{'1xx':0,'2xx':16,'3xx':0,'4xx':3,'5xx':1}},expiry:{batches:5,succeeded:4,failed:1,batchFailures:0,lastBatch:{status:'SUCCEEDED',startedAt:'2026-11-02T05:59:58Z',completedAt:'2026-11-02T05:59:59Z',processed:0,failed:0,queryFailed:false},lastSuccessfulAt:'2026-11-02T05:59:59Z',lastFailureAt:'2026-11-02T05:20:00Z'}}});
const reconciliation=()=>({checkedCount:8,mismatchCount:0,items:[]});
class Element {
  constructor(){this.children=[];this.ownText='';this.hidden=false;this.listeners={};}
  set textContent(v){this.ownText=String(v);this.children=[];}get textContent(){return this.ownText+this.children.map(x=>x.textContent).join(' ');}
  append(...v){this.children.push(...v);}replaceChildren(...v){this.children=v;this.ownText='';}addEventListener(name,handler){this.listeners[name]=handler;}
}
const deferred=()=>{let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no;});return {promise,resolve,reject};};
function fixture(request){
  const elements=new Map(),get=key=>{if(!elements.has(key))elements.set(key,new Element());return elements.get(key);};
  const session={epoch:1,me:{id:4,roles:['ROLE_ADMIN','ROLE_WAREHOUSE']}};
  const ui={$:get,node(tag,text,className){const el=new Element();if(text!==undefined)el.textContent=text;el.className=className;return el;},message(key,text=''){get(key).textContent=text;get(key).hidden=!text;},failure:error=>error.message};
  const monitor=createRuntimeMonitor({document:{querySelector:get},getSession:()=>session,request,ui});return {monitor,get,session};
}
test('累计5xx与历史失败不会把最近成功批次显示成当前失败',()=>{
  const view=summarizeRuntime(snapshot());assert.equal(view.batchLabel,'最近一批正常完成');assert.equal(view.averageMs,'2.00');assert.equal(view.serverErrors,1);
  const disabled=snapshot();disabled.expiryEnabled=false;assert.equal(summarizeRuntime(disabled).batchLabel,'自动扫描未启用');
  assert.throws(()=>summarizeRuntime({databaseStatus:'UP'}),/状态数据不完整/);
});
test('手动检查使用真实对账，部分失败时清除该区旧绿灯',async()=>{
  let failStatus=false,failReconciliation=false;
  const f=fixture(async(method,path)=>{if(path==='/api/admin/runtime'){if(failStatus)throw new Error('状态核验503');return {data:snapshot()};}if(failReconciliation)throw new Error('对账失败');return {data:reconciliation()};});
  await f.monitor.show();assert.match(f.get('#runtime-system').textContent,/数据库就绪/);assert.match(f.get('#runtime-reconciliation').textContent,/0 项差异/);
  failStatus=true;await f.monitor.refresh();assert.match(f.get('#runtime-system').textContent,/无法核验/);assert.doesNotMatch(f.get('#runtime-system').textContent,/数据库就绪/);assert.match(f.get('#runtime-reconciliation').textContent,/0 项差异/);assert.match(f.get('#runtime-note').textContent,/503/);
  failReconciliation=true;await f.monitor.refresh();assert.match(f.get('#runtime-reconciliation').textContent,/无法核验/);assert.doesNotMatch(f.get('#runtime-reconciliation').textContent,/0 项差异/);
});
test('同一次在途检查去重，重置后的旧响应不能覆盖新身份结果',async()=>{
  const old=deferred();let count=0,oldIdentity=true;
  const f=fixture(async(method,path)=>{count++;if(path==='/api/admin/runtime')return oldIdentity?old.promise:{data:snapshot()};return {data:{checkedCount:8,mismatchCount:oldIdentity?9:2}};});
  const first=f.monitor.show();const duplicate=f.monitor.refresh();assert.equal(count,2);
  f.session.epoch++;f.monitor.reset();oldIdentity=false;await f.monitor.show();assert.match(f.get('#runtime-reconciliation').textContent,/2 项差异/);
  old.resolve({data:snapshot()});await Promise.all([first,duplicate]);assert.match(f.get('#runtime-reconciliation').textContent,/2 项差异/);
  f.session.me=null;f.session.epoch++;f.monitor.reset();assert.equal(f.get('#runtime-system').textContent,'');await f.monitor.show();assert.equal(count,4);
});
test('退出后未完成的检查不能重新填入任何运行数据',async()=>{
  const pending=deferred();const f=fixture(()=>pending.promise);const work=f.monitor.show();f.session.epoch++;f.session.me=null;f.monitor.reset();pending.resolve({data:snapshot()});await work;assert.equal(f.get('#runtime-system').textContent,'');assert.equal(f.get('#runtime-expiry').textContent,'');
});
