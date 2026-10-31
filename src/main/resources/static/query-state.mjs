const dateValue=value=>/^\d{4}-\d{2}-\d{2}$/.test(value||'')&&Number.isFinite(Date.parse(value+'T00:00:00Z'))&&new Date(value+'T00:00:00Z').toISOString().slice(0,10)===value;
const amountValue=value=>/^\d{1,12}(\.\d{1,2})?$/.test(value||'');
export function parseHash(hash=''){
 const [page,raw='']=hash.replace(/^#/,'').split('?'),params=new URLSearchParams(raw);
 const number=Number(params.get('page')),size=Number(params.get('size'));
 const query={q:(params.get('q')||'').slice(0,120),status:params.get('status')||'all',page:Number.isSafeInteger(number)&&number>0&&number<=1000000?number:1,size:[8,20,50].includes(size)?size:8};
 for(const key of ['from','to'])if(dateValue(params.get(key)))query[key]=params.get(key);
 for(const key of ['minTotal','maxTotal'])if(amountValue(params.get(key)))query[key]=params.get(key);
 for(const key of ['type','action','beforeId','mismatchOnly'])if(params.has(key))query[key]=params.get(key);
 return {page:page||'overview',query};
}
export function queryParams(query={}){const params=new URLSearchParams();for(const [key,value]of Object.entries(query))if(value!==''&&value!=null)params.set(key,String(value));return params;}
export function queryHash(page,query={}){const params=queryParams(query).toString();return '#'+page+(params?'?'+params:'');}
export function validateRange(query){
 if(query.from&&query.to&&query.from>query.to)throw new Error('开始日期不能晚于结束日期。');
 if(query.minTotal&&query.maxTotal&&Number(query.minTotal)>Number(query.maxTotal))throw new Error('最低金额不能大于最高金额。');
 for(const key of ['from','to'])if(query[key]&&!dateValue(query[key]))throw new Error('请输入有效日期。');
 for(const key of ['minTotal','maxTotal'])if(query[key]&&!amountValue(query[key]))throw new Error('金额必须为非负数，最多两位小数。');
}
export function snapshotLabel(item){return {name:item.skuName||'历史商品名称未知 · SKU #'+item.skuId,code:item.skuCode||'SKU #'+item.skuId};}
