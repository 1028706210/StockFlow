const batchStates={NEVER:['等待首次扫描','neutral'],RUNNING:['正在扫描','neutral'],SUCCEEDED:['最近一批正常完成','success'],PARTIAL_FAILED:['最近一批有订单处理失败','warning'],FAILED:['最近一批查询失败','danger']};
const count=value=>typeof value==='number'&&Number.isFinite(value)&&value>=0;
export function summarizeRuntime(data){
  const metrics=data?.metrics,requests=metrics?.requests,expiry=metrics?.expiry,batch=expiry?.lastBatch;
  if(!['UP','DOWN'].includes(data?.databaseStatus)||typeof data.expiryEnabled!=='boolean'||!count(data.expiryDelayMillis)||!count(metrics?.process?.uptimeSeconds)||!count(requests?.count)||!count(requests?.durationNanos)||!count(requests?.statusClasses?.['5xx'])||!batchStates[batch?.status]||!count(batch.processed)||!count(batch.failed)||!count(expiry.batches)||!count(expiry.succeeded)||!count(expiry.failed)||!count(expiry.batchFailures))throw new Error('状态数据不完整，请重新检查。');
  const [batchLabel,batchTone]=data.expiryEnabled?batchStates[batch.status]:['自动扫描未启用','neutral'];
  return {requests:requests.count,averageMs:requests.count?(requests.durationNanos/requests.count/1e6).toFixed(2):'—',serverErrors:requests.statusClasses['5xx'],batchLabel,batchTone};
}
function date(value){if(!value)return '尚无记录';const parsed=new Date(value);return Number.isNaN(parsed.getTime())?'时间未知':parsed.toLocaleString('zh-CN',{hour12:false});}
function duration(value){const seconds=Math.floor(value);return `${Math.floor(seconds/3600)} 小时 ${Math.floor(seconds%3600/60)} 分 ${seconds%60} 秒`;}
export function createRuntimeMonitor({document,getSession,request,ui}){
  const {$,node,message,failure}=ui;
  const panels=['#runtime-system','#runtime-http','#runtime-errors','#runtime-reconciliation','#runtime-expiry'];
  let sequence=0,inFlight=null;
  function card(selector,label,value,description,tone='neutral'){$(selector).replaceChildren(node('span',label,'runtime-label'),node('strong',value,'runtime-value '+tone),node('p',description,'data-note'));}
  function unavailable(selectors){for(const selector of selectors)card(selector,selector==='#runtime-expiry'?'订单超时扫描':'检查结果','无法核验','本次读取未完成，请重新检查。','warning');}
  function renderStatus(data){
    const summary=summarizeRuntime(data),metrics=data.metrics,expiry=metrics.expiry,batch=expiry.lastBatch;
    card('#runtime-system','服务与数据库',data.databaseStatus==='UP'?'数据库就绪':'数据库未就绪',`服务已响应 · 本次运行 ${duration(metrics.process.uptimeSeconds)}。`,data.databaseStatus==='UP'?'success':'danger');
    card('#runtime-http','本次运行 API 请求',String(summary.requests),`累计平均耗时 ${summary.averageMs} ms，包含本页诊断请求。`);
    card('#runtime-errors','历史累计 5xx',String(summary.serverErrors),'这是本次运行累计值，不表示故障正在持续。',summary.serverErrors?'warning':'neutral');
    const box=$('#runtime-expiry');box.replaceChildren(node('span','订单超时扫描','runtime-label'),node('strong',summary.batchLabel,'runtime-state '+summary.batchTone));
    const details=node('dl',undefined,'runtime-facts');
    const facts=[['配置间隔',`${data.expiryDelayMillis/1000} 秒`],['本批开始',date(batch.startedAt)],['本批结束',date(batch.completedAt)],['最近正常完成',date(expiry.lastSuccessfulAt)],['历史最近失败',date(expiry.lastFailureAt)],['本批候选处理',`成功 ${batch.processed} · 失败 ${batch.failed}`],['累计扫描批次',String(expiry.batches)],['累计处理 / 失败',`${expiry.succeeded} / ${expiry.failed}`],['累计查询失败',String(expiry.batchFailures)]];
    for(const [label,value] of facts)details.append(node('dt',label),node('dd',value));box.append(details,node('p','候选处理成功仅表示没有抛出异常，不等于实际取消订单数。空批次正常完成也会更新完成时间。','data-note'));
    $('#runtime-observed').textContent='运行快照：'+date(data.observedAt)+' · 计数起点：'+date(metrics.process.startedAt);
  }
  function renderReconciliation(data){
    if(!count(data?.checkedCount)||!count(data?.mismatchCount))throw new Error('对账数据不完整，请重新检查。');
    card('#runtime-reconciliation','库存核对',data.mismatchCount+' 项差异',`本次核对 ${data.checkedCount} 种商品；仅检查，不自动修正库存。`,data.mismatchCount?'danger':'success');
  }
  function load(){
    if(!getSession().me?.roles?.includes('ROLE_ADMIN'))return Promise.resolve();
    if(inFlight)return inFlight;
    const epoch=getSession().epoch,current=++sequence;
    panels.forEach(selector=>card(selector,'正在检查','…','正在读取本次状态。'));message('#runtime-note','正在核验运行状态与库存…');$('#runtime-observed').textContent='';$('#runtime-retry').disabled=true;
    const work=(async()=>{
      try{
        const results=await Promise.allSettled([request('GET','/api/admin/runtime'),request('GET','/api/inventory/reconciliation?mismatchOnly=true&page=1&size=1')]);
        if(epoch!==getSession().epoch||current!==sequence)return;
        const errors=[];
        for(let i=0;i<results.length;i++){
          try{const result=results[i];if(result.status==='rejected')throw result.reason;(i===0?renderStatus:renderReconciliation)(result.value.data);}
          catch(error){if(error.stale)continue;unavailable(i===0?['#runtime-system','#runtime-http','#runtime-errors','#runtime-expiry']:['#runtime-reconciliation']);errors.push((i===0?'运行状态：':'库存核对：')+failure(error));}
        }
        message('#runtime-note',errors.join('；'));$('#runtime-retry').hidden=!errors.length;
      }finally{if(current===sequence&&epoch===getSession().epoch){inFlight=null;$('#runtime-retry').disabled=false;}}
    })();inFlight=work;return work;
  }
  function reset(){sequence++;inFlight=null;for(const selector of panels)$(selector).replaceChildren();message('#runtime-note');$('#runtime-observed').textContent='';$('#runtime-retry').hidden=true;$('#runtime-retry').disabled=false;}
  $('#runtime-retry').addEventListener('click',load);
  return {show:load,refresh:load,reset};
}
