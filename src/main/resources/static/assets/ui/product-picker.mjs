// 共用商品选择器；保留身份、请求顺序与提交中保护。
export function createProductPicker({state, request, ui, rememberProducts, productOptions, updateCart}) {
  const {$, node, button, money, message, failure} = ui;
  let pickerSequence = 0;
async function openProductPicker(purpose,select=null){
  if(purpose==='receipt'&&($('#receipt-form').dataset.busy==='true'||state.receiptAttempt))return;
  if(purpose==='cart'&&state.orderAttempt)return;
  state.picker={purpose,select,page:1,query:'',total:0,totalPages:1};$('#picker-search').value='';$('#picker-title').textContent=(purpose==='receipt'||purpose==='document')?'选择入库商品':purpose==='stocktake'?'选择盘点商品':'选择订单商品';
  if(!$('#picker-dialog').open)$('#picker-dialog').showModal();await loadPicker();
}
async function loadPicker(reset=false){
  const picker=state.picker;if(!picker)return;const sequence=++pickerSequence,epoch=state.epoch;if(reset){picker.page=1;picker.query=$('#picker-search').value.trim();}
  $('#picker-results').replaceChildren(node('p','正在查询全部商品…','muted'));message('#picker-note');$('#picker-pagination').replaceChildren();
  const params=new URLSearchParams({page:String(picker.page),size:'8',q:picker.query,status:picker.purpose==='cart'?'active':'all'});
  try{
    const {data}=await request('GET','/api/skus/page?'+params);
    if(epoch!==state.epoch||picker!==state.picker||sequence!==pickerSequence||!$('#picker-dialog').open)return;
    picker.page=data.page;picker.total=data.total;picker.totalPages=data.totalPages;
    const results=$('#picker-results');results.replaceChildren();
    for(const product of data.items){const choice=button('',()=>{
      if(picker!==state.picker||!$('#picker-dialog').open)return;
      if(picker.purpose==='cart'&&(state.orderAttempt||$('#cart-fieldset').disabled))return;
      if(picker.purpose==='receipt'&&(state.receiptAttempt||$('#receipt-form').dataset.busy==='true'))return;
      rememberProducts([product]);
      if(picker.purpose==='cart'){productOptions(picker.select,product.id,true);updateCart();}
      else if(picker.purpose==='document'){productOptions(picker.select,product.id);}
      else if(picker.purpose==='stocktake'){if(state.stocktakeAttempt||$('#stocktake-form').dataset.busy==='true')return;productOptions($('#stocktake-product'),product.id);}
      else{productOptions($('#receipt-product'),product.id);}
      $('#picker-dialog').close();state.picker=null;
    },'picker-result');const main=node('span');main.append(node('strong',product.name),node('small',product.code));choice.append(main,node('span',money(product.price)+(product.active?'':' · 停用'),'picker-result-meta'));results.append(choice);}
    if(!data.items.length)results.append(node('p','没有匹配的商品，换个关键词试试。','muted'));
    const previous=button('上一页',()=>{picker.page--;loadPicker();},'button secondary'),next=button('下一页',()=>{picker.page++;loadPicker();},'button secondary');previous.disabled=data.page<=1;next.disabled=data.page>=data.totalPages;
    $('#picker-pagination').replaceChildren(node('span','共 '+data.total+' 件 · '+data.page+' / '+data.totalPages),previous,next);
  }catch(error){if(!error.stale&&epoch===state.epoch&&picker===state.picker&&sequence===pickerSequence){message('#picker-note',failure(error));$('#picker-results').replaceChildren(button('重新加载',()=>loadPicker(),'button secondary'));}}
}
$('#picker-form').addEventListener('submit',event=>{event.preventDefault();loadPicker(true);});
  return {open: openProductPicker, load: loadPicker, invalidate(){pickerSequence++;}};
}
