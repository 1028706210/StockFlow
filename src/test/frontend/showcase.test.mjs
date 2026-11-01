import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {validateBase,requireShowcase} from '../../../tools/showcase/prepare.mjs';
test('展示准备拒绝外域、凭据、路径和错误环境标记',()=>{
  for(const url of ['https://example.com','http://localhost:8096/shop','http://a:b@localhost:8096','http://localhost:8096/?x=1'])assert.throws(()=>validateBase(url));
  assert.equal(validateBase('http://127.0.0.1:8096'),'http://127.0.0.1:8096');
  assert.throws(()=>requireShowcase({environment:'local'}));
  assert.doesNotThrow(()=>requireShowcase({environment:'showcase',dataset:'stockflow-showcase-v1'}));
});
test('展示清单有8个独立中文商品和真实数量，编码不重复',async()=>{
  const items=JSON.parse(await readFile(new URL('../../../tools/showcase/products.json',import.meta.url),'utf8'));
  assert.equal(items.length,8);assert.equal(new Set(items.map(item=>item.code)).size,8);
  for(const item of items){assert.match(item.name,/[\u4e00-\u9fff]/);assert.ok(item.quantity>0);assert.ok(item.specification);assert.ok(item.unit);assert.ok(item.description);}
});
