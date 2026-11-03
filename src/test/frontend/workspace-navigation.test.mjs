import test from 'node:test';
import assert from 'node:assert/strict';
import {workspaceNavigation} from '../../main/resources/static/assets/ui/workspace-navigation.mjs';

const admin={roles:['ROLE_ADMIN','ROLE_WAREHOUSE']},warehouse={roles:['ROLE_WAREHOUSE']},buyer={roles:['ROLE_BUYER']};
test('管理员一级入口从13项收敛为6项，库存旧深链接保留上下文',()=>{
  const model=workspaceNavigation('ledger',admin);
  assert.equal(model.primary.length,6);assert.equal(model.primary.find(x=>x.active).label,'库存中心');
  assert.deepEqual(model.secondary.map(x=>x.page),['inventory','ledger','alerts','stocktakes','reconciliation']);
  assert.equal(model.secondary.find(x=>x.active).page,'ledger');
});
test('系统与订单二级入口按角色过滤，买家看不到仓管分组',()=>{
  assert.deepEqual(workspaceNavigation('runtime',admin).secondary.map(x=>x.page),['audit','accounts','runtime']);
  assert.deepEqual(workspaceNavigation('audit',warehouse).secondary.map(x=>x.page),['audit']);
  assert.deepEqual(workspaceNavigation('returns',warehouse).secondary.map(x=>x.page),['fulfillment','returns']);
  const model=workspaceNavigation('orders',buyer);assert.equal(model.primary.find(x=>x.active).page,'orders');assert.equal(model.secondary.length,0);
  assert.ok(model.primary.every(x=>!['inventory','receipts','fulfillment','audit','accounts','runtime'].includes(x.page)));
});
test('没有身份时不产生业务链接，单页模块不重复显示二级导航',()=>{
  assert.equal(workspaceNavigation('overview',null).primary.length,0);
  assert.equal(workspaceNavigation('products',admin).secondary.length,0);
});
