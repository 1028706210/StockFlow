import test from 'node:test';import assert from 'node:assert/strict';
import {restoreCart,cartSummary,filterProducts} from '../public/shop-model.mjs';
const p=[{id:'c-icons',category:'svg',title:'商家图标',subtitle:'十二个原创图标',description:'适合品牌网站',currency:'CNY',suggestedPriceMinor:2900,fulfillmentReady:true},{id:'a-social',category:'canva',title:'品牌社交模板',subtitle:'六页模板',description:'可编辑',currency:'CNY',suggestedPriceMinor:5900,fulfillmentReady:false}];
test('购物袋恢复删除未知和重复商品，不信任本地存储对象',()=>{assert.deepEqual(restoreCart('["c-icons","c-icons","missing",{}]',p),['c-icons']);assert.deepEqual(restoreCart('{broken',p),[]);assert.deepEqual(restoreCart('{}',p),[]);});
test('汇总按分计算且模板未就绪阻止结算',()=>{assert.deepEqual(cartSummary(['c-icons','a-social','c-icons'],p),{items:p,total:8800,currency:'CNY',blocked:true});assert.equal(cartSummary([],p).currency,null);});
test('分类与关键词组合，无匹配返回空列表',()=>{assert.equal(filterProducts(p,'svg','品牌').length,1);assert.equal(filterProducts(p,'canva','图标').length,0);assert.equal(filterProducts(p,'all').length,2);});
