const item=(page,label,icon)=>({page,label,icon});
const inventory=[item('inventory','库存总览'),item('ledger','变动流水'),item('alerts','补货预警'),item('stocktakes','库存盘点'),item('reconciliation','库存对账')];
export function workspaceNavigation(page,me){
  const roles=Array.isArray(me?.roles)?me.roles:[],admin=roles.includes('ROLE_ADMIN'),warehouse=admin||roles.includes('ROLE_WAREHOUSE');
  if(!warehouse&&!roles.includes('ROLE_BUYER'))return {primary:[],secondary:[],label:''};
  const groups=warehouse?[
    {...item('overview','工作台','layout-dashboard'),children:[]},
    {...item('products','商品管理','package'),children:[]},
    {...item('inventory','库存中心','warehouse'),children:inventory},
    {...item('receipts','入库管理','plus'),children:[]},
    {...item('fulfillment','订单管理','truck'),children:[item('fulfillment','订单履约'),item('returns','退货处理')]},
    {...item('audit','系统管理','refresh-cw'),children:[item('audit','操作记录'),...(admin?[item('accounts','账号管理'),item('runtime','运行状态')]:[])]}
  ]:[
    {...item('overview','我的工作台','layout-dashboard'),children:[]},
    {...item('products','浏览商品','package'),children:[]},
    {...item('orders','我的订单','shopping-bag'),children:[]},
    {...item('returns','我的退货','refresh-cw'),children:[]},
    {...item('new-order','创建订单','plus'),children:[]}
  ];
  const selected=groups.find(group=>group.page===page||group.children.some(child=>child.page===page))||groups[0];
  return {label:selected.label,primary:groups.map(({children,...entry})=>({...entry,active:entry.page===selected.page})),secondary:selected.children.map(entry=>({...entry,active:entry.page===page}))};
}
export function createWorkspaceNavigation({document,node}){
  const primary=document.querySelector('#workspace-navigation'),secondary=document.querySelector('#section-navigation');
  function anchor(entry,withIcon){
    const link=node('a',undefined,entry.active?'active':'');link.href='#'+entry.page;link.dataset.nav=entry.page;
    if(entry.active)link.setAttribute('aria-current','page');
    if(withIcon){const icon=node('i',undefined,'icon icon-'+entry.icon);icon.setAttribute('aria-hidden','true');link.append(icon);}
    link.append(node('span',entry.label));return link;
  }
  function render(page,me){
    const model=workspaceNavigation(page,me);
    primary.replaceChildren(...model.primary.map(entry=>{const link=anchor(entry,true);if(entry.active&&entry.page!==page)link.setAttribute('aria-current','location');return link;}));
    secondary.replaceChildren(...model.secondary.map(entry=>anchor(entry,false)));secondary.hidden=model.secondary.length<2;secondary.setAttribute('aria-label',model.label+'功能');
  }
  function reset(){primary.replaceChildren();secondary.replaceChildren();secondary.hidden=true;}
  return {render,reset};
}
