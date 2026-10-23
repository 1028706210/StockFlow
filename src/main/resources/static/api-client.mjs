function staleError(){const error=new Error('身份已切换');error.stale=true;return error;}
export async function requestJson(getSession,method,path,body,authorization=getSession().auth,fetcher=fetch) {
  const epoch=getSession().epoch;
  let response;
  try {
    response=await fetcher(path,{method,headers:{Authorization:authorization,...(body===undefined?{}:{'Content-Type':'application/json'})},
      body:body===undefined?undefined:JSON.stringify(body),credentials:'omit',cache:'no-store',signal:AbortSignal.timeout(20000)});
  } catch {
    if(epoch!==getSession().epoch)throw staleError();
    throw new Error('连接未完成。请检查服务；提交类操作请保留原请求标识重试。');
  }
  if(epoch!==getSession().epoch)throw staleError();
  let result;
  try {result=await response.json();} catch {
    if(epoch!==getSession().epoch)throw staleError();
    throw new Error('服务返回了无法识别的响应，请稍后重试。');
  }
  if(epoch!==getSession().epoch)throw staleError();
  if(!response.ok){
    const messages={
      UNAUTHENTICATED:'身份验证失败，请检查账号和演示口令。',
      FORBIDDEN:'当前角色没有此操作权限。',
      INSUFFICIENT_STOCK:'可用库存不足，请调整商品或数量。',
      IDEMPOTENCY_CONFLICT:'此请求标识已用于不同的内容，请先核对原操作。',
      SKU_NOT_FOUND:'商品不存在或已停用，请刷新商品列表。',
      SKU_CODE_EXISTS:'SKU 编码已存在，请使用其他编码。',
      NOT_FOUND:'未找到该记录，或该记录不属于当前买家。',
      INVALID_REQUEST:'填写内容不符合要求，请检查编码、金额与整数数量。'
    };
    const error=new Error((messages[result.code] || '操作失败，请刷新后重试。')+(result.requestId?'（参考号：'+result.requestId+'）':''));
    error.status=response.status;throw error;
  }
  return {status:response.status,data:result};
}
