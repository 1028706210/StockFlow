// 买家入口只改变信息组织，商品、身份、下单和履约继续使用主应用。
export const isStorefrontPath = path => /^\/shop\/?$/.test(path || '');
export function createStorefrontUI({document,location,node,button,money}) {
  const enabled=isStorefrontPath(location.pathname),$=selector=>document.querySelector(selector);
  if(enabled){
    document.body.dataset.surface='storefront';
    document.title='StockFlow · 商品订购';
    $('#login-user').value='buyer';
    $('.login-heading h1').textContent='选好商品，交给仓库履约。';
    $('.login-heading .lead').textContent='与仓管后台共享商品与订单，从提交预留到发货收货，每一步都有记录。';
    $('.login-foot').textContent='STOCKFLOW / 买家订购入口';
  }
  function accept(me){
    if(!enabled)return true;
    if(!me.roles.includes('ROLE_BUYER')){location.replace('/#overview');return false;}
    if(!location.hash)location.hash='#products';
    return true;
  }
  function auth(register){
    if(!enabled)return;
    $('#auth-title').textContent=register?'创建买家账号':'登录订购工作台';
    $('#auth-description').textContent=register?'注册后可选购商品、跟踪本人订单。':'使用买家账号登录；商品、订单与仓管后台保持一致。';
  }
  function page(page){
    if(!enabled)return;
    $('#storefront-header').hidden=false;
    const titles={products:['商品目录','选好商品，开始一笔订购。','按名称或编码查找在售商品。提交订单时由后台核验可用库存。'],overview:['订购概览','订单有进度，协作有依据。','关注待确认、待发货与待收货，不把订单金额当作实付金额。'],orders:['我的订单','每一笔订购，进度都清楚。','提交后确认订单，仓管发货后由你确认收货。'],returns:['我的退货','整单申请，验收后回库。','仅已完成的本人订单可申请；退货不包含资金退款。'],'new-order':['订购单','核对商品，提交后预留。','金额与库存以服务端结果为准；提交后还需确认订单。']};
    const copy=titles[page]||titles.overview;
    $('#breadcrumb-page').textContent=copy[0];$('#page-title').textContent=copy[1];$('#page-kicker').textContent='STOCKFLOW / '+copy[0];$('#page-description').textContent=copy[2];
    $('#storefront-process').hidden=page!=='products'&&page!=='new-order';
    $('#storefront-catalog').hidden=false;
    $('.workspace-footer').textContent='StockFlow · 买家订购端 / 商品、订单与仓储履约共用一套业务数据';
    $('#products-search').placeholder='搜索商品名称或 SKU 编码';
  }
  function catalog(list,{detail,pick}){
    if(!enabled)return;
    const grid=$('#storefront-catalog');grid.replaceChildren();
    for(const item of list.items){
      const card=node('article',undefined,'store-product');
      const top=node('div',undefined,'store-product-top');top.append(node('span',item.code,'store-sku'),node('span','在售商品','store-sale'));
      const title=button(item.name,()=>detail(item.id),'store-product-name');title.disabled=list.loading||!!list.error;
      const price=node('div',undefined,'store-price');price.append(node('strong',money(item.price)),node('span','/ 件'));
      const meta=node('p','库存以下单校验为准。','store-stock-note');
      const actions=node('div',undefined,'store-product-actions');
      const more=button('商品详情',()=>detail(item.id),'button secondary'),choose=button('加入订购单',()=>pick(item.id),'button primary');
      more.disabled=choose.disabled=list.loading||!!list.error||!item.active;
      actions.append(more,choose);card.append(top,title,price,meta,actions);grid.append(card);
    }
    grid.setAttribute('aria-busy',String(list.loading));
  }
  function draft(count){if(enabled)$('#storefront-draft-count').textContent=count==null?'待核对':count+' 种';}
  function clear(){if(enabled){$('#storefront-catalog').replaceChildren();draft(0);}}
  if(enabled)auth(false);
  return {enabled,accept,auth,page,catalog,draft,clear};
}
