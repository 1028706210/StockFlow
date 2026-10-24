import test from 'node:test';
import assert from 'node:assert/strict';
import {requestJson} from '../../main/resources/static/api-client.mjs';
const deferred=()=>{let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no;});return {promise,resolve,reject};};
test('切换账号发生在响应正文读取期间时，旧结果不能返回',async()=>{
 const state={epoch:1,csrf:'old'},body=deferred(),reading=deferred();
 const pending=requestJson(()=>state,'GET','/api/orders',undefined,undefined,async()=>({ok:true,status:200,json:()=>{reading.resolve();return body.promise;}}));
 await reading.promise;state.epoch++;state.csrf='new';body.resolve([{id:1}]);await assert.rejects(pending,e=>e.stale===true);
});
test('切换账号后的旧正文解析错误不会变成新账号的错误提示',async()=>{
 const state={epoch:1},body=deferred(),reading=deferred();
 const pending=requestJson(()=>state,'GET','/api/orders',undefined,undefined,async()=>({ok:true,status:200,json:()=>{reading.resolve();return body.promise;}}));
 await reading.promise;state.epoch++;body.reject(new Error('old body failed'));await assert.rejects(pending,e=>e.stale===true);
});
test('浏览器写入使用同源Cookie和CSRF，不发送Basic凭据',async()=>{
 const state={epoch:1,csrf:'csrf-example',auth:'Basic obsolete'};let captured;
 const result=await requestJson(()=>state,'POST','/api/orders',{items:[]},undefined,async(path,options)=>{captured={path,options};return {ok:true,status:201,json:async()=>({id:7})};});
 assert.deepEqual(result,{status:201,data:{id:7}});assert.equal(captured.options.headers['X-CSRF-TOKEN'],'csrf-example');
 assert.equal(captured.options.headers.Authorization,undefined);assert.equal(captured.options.credentials,'same-origin');
});
test('读取请求不发送CSRF头',async()=>{
 let captured;await requestJson(()=>({epoch:1,csrf:'secret'}),'GET','/api/orders',undefined,undefined,async(_,options)=>{captured=options;return {ok:true,status:200,json:async()=>[]};});
 assert.equal(captured.headers['X-CSRF-TOKEN'],undefined);assert.equal(captured.credentials,'same-origin');
});
test('登录表单按标准编码发送并保留特殊字符',async()=>{
 let captured;const form=new URLSearchParams({username:'buyer',password:'example+&='});
 await requestJson(()=>({epoch:1,csrf:'token'}),'POST','/api/auth/login',form,undefined,async(_,options)=>{captured=options;return {ok:true,status:200,json:async()=>({id:1})};});
 assert.equal(captured.headers['Content-Type'],'application/x-www-form-urlencoded');assert.equal(captured.body,form.toString());
});
test('退出204不解析空响应正文',async()=>{
 const result=await requestJson(()=>({epoch:1,csrf:'token'}),'POST','/api/auth/logout',undefined,undefined,async()=>({ok:true,status:204,json:()=>{throw new Error('must not parse empty body');}}));
 assert.deepEqual(result,{status:204,data:null});
});
