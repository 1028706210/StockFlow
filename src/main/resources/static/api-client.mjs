function staleError(){const error=new Error('身份已切换');error.stale=true;return error;}
export async function requestJson(getSession,method,path,body,csrf=getSession().csrf,fetcher=fetch) {
 const epoch=getSession().epoch,headers={};
 if(!['GET','HEAD','OPTIONS'].includes(method.toUpperCase())&&csrf)headers['X-CSRF-TOKEN']=csrf;
 const form=body instanceof URLSearchParams;
 if(body!==undefined)headers['Content-Type']=form?'application/x-www-form-urlencoded':'application/json';
 let response;
 try{
  response=await fetcher(path,{method,headers,body:body===undefined?undefined:form?body.toString():JSON.stringify(body),
   credentials:'same-origin',cache:'no-store',signal:AbortSignal.timeout(20000)});
 }catch{if(epoch!==getSession().epoch)throw staleError();throw new Error('连接未完成。请检查服务；提交类操作请保留原请求标识重试。');}
 if(epoch!==getSession().epoch)throw staleError();
 let result=null;
 if(response.status!==204){
  try{result=await response.json();}catch{if(epoch!==getSession().epoch)throw staleError();throw new Error('服务返回了无法识别的响应，请稍后重试。');}
 }
 if(epoch!==getSession().epoch)throw staleError();
 if(!response.ok){
  const messages={RETURN_EXISTS:'该订单已有待处理或已确认退货，请查看退货单。',INVALID_RETURN_STATE:'退货状态已变化，请重新打开单据。',INVALID_RECEIPT_STATE:'入库单状态已变化，请重新打开单据。',INVENTORY_LIMIT:'入库后库存将超过上限，请核对数量。',EXPORT_LIMIT:'结果超过 10000 条，请缩小筛选范围后导出。',STOCK_CHANGED:'库存已变化。请取消这张盘点单，重新核对实盘数量后建立新单。',RESERVED_STOCK:'实盘数量低于已预留量，请先核对库存和有效订单。',INVALID_STOCKTAKE_STATE:'盘点状态已变化，请重新打开盘点单。',METHOD_NOT_ALLOWED:'此操作不受支持，请刷新页面。',UNSUPPORTED_MEDIA_TYPE:'提交格式不正确，请刷新页面。',UNAUTHENTICATED:'身份验证失败，请检查账号和密码。',FORBIDDEN:'当前操作缺少权限或登录已过期，请刷新后重试。',
   INSUFFICIENT_STOCK:'可用库存不足，请调整商品或数量。',IDEMPOTENCY_CONFLICT:'此请求标识已用于不同的内容，请先核对原操作。',
   SKU_NOT_FOUND:'商品不存在或已停用，请刷新商品列表。',SKU_CODE_EXISTS:'SKU 编码已存在，请使用其他编码。',
   NOT_FOUND:'未找到该记录，或该记录不属于当前账号。',INVALID_REQUEST:'填写内容不符合要求，请检查字段格式和整数数量。',
   USERNAME_TAKEN:'账号已存在，请更换账号或直接登录。',INVALID_ORDER_STATE:'订单状态已变化，请刷新后重试。',
   ORDER_EXPIRED:'订单已超过确认期限，预留库存将由超时任务释放。',INVENTORY_CONFLICT:'库存记录暂时无法完成本次操作，请联系仓管核对。'};
  const error=new Error((messages[result?.code]||'操作失败，请刷新后重试。')+(result?.requestId?'（参考号：'+result.requestId+'）':''));
  error.status=response.status;error.code=result?.code;throw error;
 }
 return {status:response.status,data:result};
}
