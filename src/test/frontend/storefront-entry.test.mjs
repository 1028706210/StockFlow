import test from 'node:test';
import assert from 'node:assert/strict';
import {createEntry} from '../../../storefront/start.mjs';
import {isStorefrontPath} from '../../main/resources/static/assets/ui/storefront.mjs';
test('仅shop入口选择买家布局，不改变原仓管入口',()=>{
  for(const p of ['/shop','/shop/'])assert.equal(isStorefrontPath(p),true);
  for(const p of ['/',undefined,'/shopping','/shop/other'])assert.equal(isStorefrontPath(p),false);
});
test('8094迁移入口只跳转本机Java前台，不接收登录或订单写入',async()=>{
  const server=createEntry();await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const base='http://127.0.0.1:'+server.address().port;
  try{
    const r=await fetch(base,{redirect:'manual'});assert.equal(r.status,302);assert.equal(r.headers.get('location'),'http://127.0.0.1:8082/shop#products');assert.equal(r.headers.get('set-cookie'),null);
    assert.equal((await fetch(base+'/api/auth/login',{method:'POST',redirect:'manual'})).status,410);
    assert.equal((await fetch(base+'/api/orders',{redirect:'manual'})).status,410);
  }finally{await new Promise(r=>server.close(r));}
});
test('迁移目标拒绝外部地址、凭据与循环，支持容器端口',()=>{
  for(const backend of ['https://example.com','http://example.com','http://user:pass@localhost:8082','http://127.0.0.1:8094','http://localhost/path'])assert.throws(()=>createEntry({backend}));
  const server=createEntry({backend:'http://127.0.0.1:8083'});server.close();
});
