import test from 'node:test';
import assert from 'node:assert/strict';
import {orderTimeline} from '../../main/resources/static/ui-model.mjs';
test('新订单只完成创建阶段，后续阶段没有伪造时间',()=>{
 const rows=orderTimeline({status:'PENDING_CONFIRM',createdAt:'2026-10-26T12:00:00'});assert.equal(rows.length,4);assert.equal(rows[0].current,true);assert.equal(rows[0].at,'2026-10-26T12:00:00');assert.equal(rows[1].done,false);assert.equal(rows[1].at,null);
});
test('已完成历史订单显示所有业务阶段，缺失时间继续为空',()=>{
 const rows=orderTimeline({status:'COMPLETED',createdAt:'2026-10-20T12:00:00'});assert.ok(rows.every(row=>row.done));assert.equal(rows[3].current,true);assert.equal(rows[1].at,null);assert.equal(rows[2].at,null);assert.equal(rows[3].at,null);
});
test('取消时间线保留真实原因和时间，不画未发生的发货',()=>{
 for(const reason of ['USER','EXPIRED']){const rows=orderTimeline({status:'CANCELLED',cancelReason:reason,cancelledAt:'2026-10-26T13:00:00'});assert.equal(rows.length,2);assert.equal(rows[1].key,'cancelled');assert.equal(rows[1].at,'2026-10-26T13:00:00');assert.equal(rows[1].label,reason==='EXPIRED'?'超时取消':'买家取消');assert.equal(rows[1].current,true);}
});
test('已发货订单保留服务端记录的时间和当前进度',()=>{
 const rows=orderTimeline({status:'SHIPPED',confirmedAt:'2026-10-26T13:00:00',shippedAt:'2026-10-26T14:00:00'});assert.equal(rows[2].at,'2026-10-26T14:00:00');assert.equal(rows[2].current,true);assert.equal(rows[3].done,false);assert.equal(rows[1].at,'2026-10-26T13:00:00');
});
