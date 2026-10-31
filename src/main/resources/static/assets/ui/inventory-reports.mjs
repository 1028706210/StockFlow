import {validateRange} from '../../query-state.mjs';

export function newInventoryReports(){return Object.fromEntries(['ledger','alerts'].map(kind=>[kind,{items:[],cursor:null,history:[],nextCursor:null,sequence:0,loading:false,error:'',total:0,query:'',type:''}]));}

// 库存流水和预警的加载、渲染及游标逻辑在此维护。
// 引用详情通过回调交给主程序，不依赖其他页面的内部变量。
export function createInventoryReports({state, request, warehouse, ui, readQuery, persistQuery, onReference, openStockPolicy, openAlertReceipt}) {
  const {$, node, button, message, failure, badge, cell, productCell, empty, time} = ui;
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
  for(const key of ['from','to'])if(kind==='ledger'&&$('#ledger-'+key).value)params.set(key,$('#ledger-'+key).value);persistQuery(kind,{...readQuery(kind),...Object.fromEntries(params)});

  try{
    validateRange(Object.fromEntries(params));const {data}=await request('GET','/api/inventory/'+kind+'?'+params);
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
  const types={INBOUND:['入库','success'],RESERVE:['订单预留','pending'],RELEASE:['取消释放','neutral'],RETURN_IN:['退货入库','success'],SHIP:['发货出库','success'],ADJUST_IN:['盘盈调整','success'],ADJUST_OUT:['盘亏调整','pending']};
  const delta=value=>node('span',value>0?'+'+value:String(value),'delta '+(value>0?'positive':value<0?'negative':'neutral'));
  for(const item of report.items){
    const row=node('tr');
    if(kind==='ledger'){
      cell(row,time(item.createdAt),'code');cell(row,productCell(item,item.code));
      const [label,tone]=types[item.operationType]||[item.operationType,'neutral'];cell(row,badge(label,tone));
      cell(row,delta(item.onHandDelta),'number');cell(row,delta(item.reservedDelta),'number');
      const reference=item.returnId?button('RT-'+item.returnId,()=>onReference('returns',item.returnId)):item.receiptId?button('RC-'+item.receiptId,()=>onReference('receipts',item.receiptId)):item.orderId?button('SF-'+item.orderId,()=>onReference('orders',item.orderId)):item.stocktakeId?button('ST-'+item.stocktakeId,()=>onReference('stocktakes',item.stocktakeId)):node('span','—');if(reference.tagName!=='SPAN')reference.disabled=report.loading||!!report.error;cell(row,reference);
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

  return {load: loadInventoryReport, render: renderInventoryReport};
}
