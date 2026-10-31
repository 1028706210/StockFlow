import test from 'node:test';
import assert from 'node:assert/strict';
import * as model from '../../main/resources/static/ui-model.mjs';
test('买家待确认可以确认或取消，已发货可以收货',()=>{
 assert.deepEqual(model.orderActions?.('PENDING_CONFIRM','BUYER'),['confirm','cancel']);
 assert.deepEqual(model.orderActions?.('SHIPPED','BUYER'),['complete']);
});
test('仓管只对已确认订单提供发货操作',()=>{
 assert.deepEqual(model.orderActions?.('CONFIRMED','WAREHOUSE'),['ship']);
 assert.deepEqual(model.orderActions?.('PENDING_CONFIRM','WAREHOUSE'),[]);
 assert.deepEqual(model.orderActions?.('CONFIRMED','ADMIN'),['ship']);
});
test('已取消已完成和未知状态不提供写入操作',()=>{
 for(const role of ['BUYER','WAREHOUSE'])for(const status of ['CANCELLED','COMPLETED','UNKNOWN'])
  assert.deepEqual(model.orderActions?.(status,role),[]);
});
test('未知身份不获得订单操作',()=>assert.deepEqual(model.orderActions?.('PENDING_CONFIRM','UNKNOWN'),[]));
