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
  const messages={UNAUTHENTICATED:'身份验证失败，请检查账号和密码。',FORBIDDEN:'当前操作缺少权限或登录已过期，请刷新后重试。',
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
