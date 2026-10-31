export function restoreCart(raw, products) {
  let ids;try {ids=JSON.parse(raw||'[]');} catch {return [];}
  if(!Array.isArray(ids))return [];
  return [...new Set(ids.filter(id=>typeof id==='string'&&products.some(p=>p.id===id)))];
}
export function cartSummary(ids,products){
  const items=[...new Set(ids)].map(id=>products.find(p=>p.id===id)).filter(Boolean);
  const currencies=new Set(items.map(p=>p.currency));
  return {items,total:items.reduce((n,p)=>n+p.suggestedPriceMinor,0),currency:currencies.size===1?items[0].currency:null,blocked:items.some(p=>!p.fulfillmentReady)};
}
export function filterProducts(products,category,query=''){
  const word=query.trim().toLocaleLowerCase();
  return products.filter(p=>(!category||category==='all'||p.category===category)&&(!word||`${p.title} ${p.subtitle} ${p.description}`.toLocaleLowerCase().includes(word)));
}
