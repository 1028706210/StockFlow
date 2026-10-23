import test from 'node:test';
import assert from 'node:assert/strict';
import { parseQuantity, priceToCents, cartPreview, paginateRows, stockState } from '../../main/resources/static/ui-model.mjs';

test('数量必须为普通十进制正整数，禁止小数和指数写法', () => {
  assert.equal(parseQuantity(' 2 '), 2);
  for (const value of ['1.9','1e2','',0,-1,100,'abc'])
    assert.throws(() => parseQuantity(value), /数量/);
});
test('入库数量使用独立上限', () => {
  assert.equal(parseQuantity('1000000',1000000),1000000);
  assert.throws(() => parseQuantity('1000001',1000000), /数量/);
});
test('金额以分计算，避免小数乘加误差', () => {
  assert.equal(priceToCents('0.10'),10);
  assert.equal(priceToCents(12.5),1250);
  assert.equal(priceToCents('999999.99'),99999999);
  assert.throws(() => priceToCents('1.001'));
});
const products=[{id:1,name:'杯子',price:0.10,active:true},{id:2,name:'笔记本',price:12.5,active:true}];
test('下单明细合并排序，预览金额按整数分相加', () => {
  const rows=[{skuId:2,quantity:'2'},{skuId:1,quantity:1},{skuId:1,quantity:2}];
  const original=structuredClone(rows);
  assert.deepEqual(cartPreview(rows,products),{items:[{skuId:1,quantity:3},{skuId:2,quantity:2}],totalCents:2530});
  assert.deepEqual(rows,original);
});
test('空订单、无效商品和合并后超量不能提交', () => {
  assert.throws(()=>cartPreview([],products),/商品/);
  assert.throws(()=>cartPreview([{skuId:'',quantity:1}],products),/商品/);
  assert.throws(()=>cartPreview([{skuId:99,quantity:1}],products),/商品/);
  assert.throws(()=>cartPreview([{skuId:1,quantity:60},{skuId:1,quantity:40}],products),/99/);
});
test('停用商品和不安全的数字 ID 被拒绝', () => {
  assert.throws(()=>cartPreview([{skuId:1,quantity:1}],[{...products[0],active:false}]),/商品/);
  assert.throws(()=>cartPreview([{skuId:9007199254740992,quantity:1}],products),/商品/);
});
test('每单最多十种商品', () => {
  const many=Array.from({length:11},(_,i)=>({id:i+1,name:'SKU',price:1,active:true}));
  assert.throws(()=>cartPreview(many.map(p=>({skuId:p.id,quantity:1})),many),/10/);
});
test('表格搜索匹配名称和编码，保留原数据', () => {
  const rows=[{code:'MUG',name:'海盐杯'},{code:'BOOK',name:'笔记本'}];
  assert.deepEqual(paginateRows(rows,'mug',1,8,['code','name']).rows,[rows[0]]);
  assert.deepEqual(paginateRows(rows,'海盐',1,8,['code','name']).rows,[rows[0]]);
  assert.equal(rows.length,2);
});
test('筛选后自动收敛页码，空数据保留可用分页信息', () => {
  const rows=Array.from({length:10},(_,i)=>({id:i+1}));
  const result=paginateRows(rows,'',9,8,['id']);
  assert.equal(result.page,2); assert.equal(result.totalPages,2);
  assert.deepEqual(result.rows,[rows[8],rows[9]]);
  assert.deepEqual(paginateRows(rows,'no match',9,8,['id']),{rows:[],page:1,totalPages:1,total:0});
});
test('库存状态使用可用量，不将已预留库存算作可售', () => {
  assert.equal(stockState({onHand:10,reserved:10,available:0}).key,'empty');
  assert.equal(stockState({onHand:100,reserved:95,available:5}).key,'low');
  assert.equal(stockState({onHand:100,reserved:0,available:100}).key,'healthy');
});
