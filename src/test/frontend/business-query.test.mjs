import test from 'node:test';
import assert from 'node:assert/strict';
import {parseHash,queryHash,queryParams,snapshotLabel,validateRange} from '../../main/resources/static/query-state.mjs';
import {createDocumentAttempt} from '../../main/resources/static/document-state.mjs';
test('hash state roundtrips literal search, pagination, dates and decimal bounds',()=>{
 const value={q:'茶 & 100%',status:'COMPLETED',page:3,size:20,from:'2026-10-01',to:'2026-10-27',minTotal:'0.10',maxTotal:'120.00'};
 assert.deepEqual(parseHash(queryHash('orders',value)),{page:'orders',query:value});
 assert.equal(queryParams(value).get('minTotal'),'0.10');
});
test('hash bounds reject impossible dates and unsafe paging while old links work',()=>{
 assert.deepEqual(parseHash('#orders').query,{q:'',status:'all',page:1,size:8});
 const {query}=parseHash('#orders?page=-1&size=999&from=2026-02-30&minTotal=NaN');
 assert.equal(query.page,1);assert.equal(query.size,8);assert.equal(query.from,undefined);assert.equal(query.minTotal,undefined);
 assert.throws(()=>validateRange({from:'2026-10-27',to:'2026-10-01'}),/日期/);
 assert.throws(()=>validateRange({minTotal:'20',maxTotal:'10'}),/金额/);
});
test('historical names never substitute current catalog names',()=>{
 assert.deepEqual(snapshotLabel({skuId:4,skuName:null,skuCode:null}),{name:'历史商品名称未知 · SKU #4',code:'SKU #4'});
 assert.deepEqual(snapshotLabel({skuId:4,skuName:'旧名称',skuCode:'OLD'}),{name:'旧名称',code:'OLD'});
});
test('uncertain create keeps original immutable payload and request key; identity reset invalidates response',async()=>{
 let epoch=1,next=0;const attempt=createDocumentAttempt(()=>epoch,()=>`key-${++next}`);
 const payload=attempt.prepare({source:'原始',items:[{skuId:1,quantity:3}]});
 assert.equal(attempt.prepare({source:'改变'}),payload);assert.equal(payload.requestKey,'key-1');
 let resolve;const response=new Promise(done=>resolve=done);const pending=attempt.submit(()=>response);epoch++;attempt.reset();
 resolve({data:{id:9}});assert.equal(await pending,null);assert.equal(attempt.payload,null);
 assert.equal(attempt.prepare({source:'新身份'}).requestKey,'key-2');
});
test('create retry uses same payload after network failure and suppresses concurrent submit',async()=>{
 const attempt=createDocumentAttempt(()=>1,()=> 'same-key');attempt.prepare({orderId:8,reason:'破损'});
 await assert.rejects(attempt.submit(async()=>{throw new Error('offline');}),/offline/);
 assert.equal(attempt.payload.requestKey,'same-key');let resolve;const pending=attempt.submit(()=>new Promise(done=>resolve=done));
 assert.equal(await attempt.submit(()=>assert.fail('duplicate request')),null);
 resolve({data:{id:3}});assert.equal((await pending).data.id,3);assert.equal(attempt.payload,null);
});

test('malformed month never crashes hash restoration',()=>{assert.equal(parseHash('#orders?from=2026-99-99').query.from,undefined);});
