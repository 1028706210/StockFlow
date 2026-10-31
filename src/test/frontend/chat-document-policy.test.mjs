import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createReplyEngine} from '../../main/resources/static/assets/ui/chat/reply-core.js';
const knowledge=JSON.parse(readFileSync(new URL('../../main/resources/static/assets/ui/chat/knowledge.json',import.meta.url),'utf8'));
const engine=()=>createReplyEngine({client:{},knowledge});
test('咨询助手说明整单退货和验收入库边界，兼容繁体提问',async()=>{
 const response=await engine().reply('如何退貨');assert.equal(response.intent,'returns');assert.match(response.reply,/已完成订单/);assert.match(response.reply,/整单/);assert.match(response.reply,/不涉及退款/);
});
test('咨询助手指引正式入库单，退款仍不承诺真实资金操作',async()=>{
 const receipt=await engine().reply('如何建立入库单');assert.equal(receipt.intent,'receipts');assert.match(receipt.reply,/草稿/);assert.match(receipt.reply,/确认/);const payment=await engine().reply('退款');assert.equal(payment.intent,'payment');assert.match(payment.reply,/未接入真实支付/);
});
