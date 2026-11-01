// 商品资料的纯显示与表单转换，不读取会话或发起请求。
export function productSummary(product){
  return [product.category,product.specification].filter(Boolean).join(' · ')||'规格待补充';
}
export function productFields(form){
  return Object.fromEntries(['category','specification','unit','description'].map(key=>[key,String(form.get(key)||'').trim()||(key==='unit'?'件':'')]));
}
const artworkCodes=new Set(['CUP','NOTE','PEN','BAG','BOX','CABLE','BOTTLE','POUCH']);
export function productArtwork(product){
  const key=String(product.code||'').replace(/^SF-DEMO-/,'');
  return product.code==='SF-DEMO-'+key&&artworkCodes.has(key)?'/assets/ui/products/'+key.toLowerCase()+'.svg':null;
}
