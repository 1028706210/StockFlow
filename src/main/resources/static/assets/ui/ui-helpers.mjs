// 通用界面工具只依赖传入的 DOM，不读取业务状态。
export function createUiHelpers(document, Node) {
  const $ = selector => document.querySelector(selector);
  const all = selector => [...document.querySelectorAll(selector)];
const money = value => new Intl.NumberFormat('zh-CN',{style:'currency',currency:'CNY'}).format(value);
const time = value => value ? new Intl.DateTimeFormat('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date(value)) : '—';
function node(tag,text,className) {
  const item=document.createElement(tag);
  if(text!==undefined)item.textContent=text;
  if(className)item.className=className;
  return item;
}
function button(text,action,className='text-button') {
  const item=node('button',text,className);item.type='button';item.addEventListener('click',action);return item;
}
function message(selector,text='') {$(selector).textContent=text;$(selector).hidden=!text;}
function failure(error) {return error.message || '操作未完成，请重试。';}
function badge(label,tone='neutral'){return node('span',label,'badge '+tone);}
function cell(row,text,className){const td=node('td',undefined,className);if(text instanceof Node)td.append(text);else td.textContent=text;row.append(td);return td;}
function productCell(product,code){
  const value=node('div');value.append(node('strong',product?.name || '商品 #'+(product?.id || '—')));
  if(code)value.append(node('span',code,'subtext code'));return value;
}
function empty(target,title,description,action){
  const container=$(target);container.replaceChildren(node('strong',title),node('p',description));
  if(action)container.append(action);container.hidden=false;
}
  return {$, all, money, time, node, button, message, failure, badge, cell, productCell, empty};
}
