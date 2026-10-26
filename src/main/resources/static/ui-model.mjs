export function parseQuantity(value, max = 99) {
  const text = String(value).trim();
  if (!/^\d+$/.test(text)) throw new Error('数量必须为正整数');
  const quantity = Number(text);
  if (!Number.isSafeInteger(quantity) || quantity < 1 || quantity > max)
    throw new Error('数量须在 1–' + max + ' 之间');
  return quantity;
}
export function priceToCents(value) {
  const text = String(value);
  if (!/^\d+(\.\d{1,2})?$/.test(text)) throw new Error('商品价格无效');
  const [whole, fraction = ''] = text.split('.');
  const cents = Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
  if (!Number.isSafeInteger(cents) || cents > 99999999) throw new Error('商品价格无效');
  return cents;
}
export function cartPreview(rows, products) {
  if (!rows.length) throw new Error('请至少选择一种商品');
  const catalog = new Map(products.map(p => [p.id, p]));
  const merged = new Map();
  for (const row of rows) {
    const id = Number(row.skuId), product = catalog.get(id);
    if (!Number.isSafeInteger(id) || id < 1 || !product || !product.active)
      throw new Error('请选择有效的在售商品');
    const quantity = (merged.get(id) || 0) + parseQuantity(row.quantity);
    if (quantity > 99) throw new Error('同一商品合计数量不能超过 99');
    merged.set(id, quantity);
  }
  if (merged.size > 10) throw new Error('每单最多选择 10 种商品');
  const items = [...merged].sort((a,b) => a[0]-b[0]).map(([skuId,quantity]) => ({skuId,quantity}));
  const totalCents = items.reduce((sum,line) => sum + priceToCents(catalog.get(line.skuId).price) * line.quantity, 0);
  return {items,totalCents};
}
export function paginateRows(rows, query, page, size, keys) {
  const term = String(query).trim().toLocaleLowerCase();
  const filtered = rows.filter(row => !term || keys.some(key => String(row[key] ?? '').toLocaleLowerCase().includes(term)));
  const totalPages = Math.max(1,Math.ceil(filtered.length / size));
  const current = Math.min(Math.max(1,page),totalPages);
  return {rows:filtered.slice((current-1)*size,current*size),page:current,totalPages,total:filtered.length};
}
export function stockState(stock) {
  if (stock.available <= 0) return {key:'empty',label:'暂无可用',tone:'neutral'};
  if (stock.available < (stock.safetyStock ?? 10)) return {key:'low',label:'低库存',tone:'warning'};
  return {key:'healthy',label:'库存充足',tone:'success'};
}

export function orderActions(status, role) {
  if (role === 'BUYER') {
    if (status === 'PENDING_CONFIRM') return ['confirm', 'cancel'];
    if (status === 'SHIPPED') return ['complete'];
  }
  if ((role === 'WAREHOUSE' || role === 'ADMIN') && status === 'CONFIRMED') return ['ship'];
  return [];
}

export function orderTimeline(order) {
  const stages=[['created','订单创建','createdAt'],['confirmed','买家确认','confirmedAt'],['shipped','仓库发货','shippedAt'],['completed','确认收货','completedAt']];
  if(order.status==='CANCELLED')return [
    {key:'created',label:'订单创建',at:order.createdAt??null,done:true,current:false},
    {key:'cancelled',label:order.cancelReason==='EXPIRED'?'超时取消':'买家取消',at:order.cancelledAt??null,done:true,current:true}
  ];
  const index={PENDING_CONFIRM:0,CONFIRMED:1,SHIPPED:2,COMPLETED:3}[order.status]??0;
  return stages.map(([key,label,field],n)=>({key,label,at:order[field]??null,done:n<=index,current:n===index}));
}
