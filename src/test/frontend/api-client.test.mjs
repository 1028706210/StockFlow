import test from 'node:test';
import assert from 'node:assert/strict';
import {requestJson} from '../../main/resources/static/api-client.mjs';
const deferred=()=>{let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no;});return {promise,resolve,reject};};
test('切换账号发生在响应正文读取期间时，旧结果不能返回',async()=>{
  const state={epoch:1,auth:'old'},body=deferred(),reading=deferred();
  const pending=requestJson(()=>state,'GET','/api/orders',undefined,undefined,async()=>({ok:true,status:200,json:()=>{reading.resolve();return body.promise;}}));
  await reading.promise;state.epoch++;state.auth='new';body.resolve([{id:1}]);
  await assert.rejects(pending,error=>error.stale===true);
});
test('切换账号后的旧正文解析错误不会变成新账号的错误提示',async()=>{
  const state={epoch:1,auth:'old'},body=deferred(),reading=deferred();
  const pending=requestJson(()=>state,'GET','/api/orders',undefined,undefined,async()=>({ok:true,status:200,json:()=>{reading.resolve();return body.promise;}}));
  await reading.promise;state.epoch++;body.reject(new Error('old body failed'));
  await assert.rejects(pending,error=>error.stale===true);
});
test('正常请求只发送同源账号头，返回服务端状态和内容',async()=>{
  const state={epoch:1,auth:'Basic demo'};let captured;
  const result=await requestJson(()=>state,'POST','/api/orders',{items:[]},undefined,async(path,options)=>{captured={path,options};return {ok:true,status:201,json:async()=>({id:7})};});
  assert.deepEqual(result,{status:201,data:{id:7}});
  assert.equal(captured.options.headers.Authorization,'Basic demo');
  assert.equal(captured.options.credentials,'omit');
});
