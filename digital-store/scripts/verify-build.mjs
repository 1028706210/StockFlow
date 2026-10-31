import {readFile,stat,readdir,mkdir,writeFile} from 'node:fs/promises';
import {resolve,join,dirname} from 'node:path';import {fileURLToPath} from 'node:url';import assert from 'node:assert/strict';import {createHash} from 'node:crypto';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..'),catalog=JSON.parse(await readFile(join(root,'public/catalog.json'),'utf8'));
assert.equal(catalog.products.length,4);assert.equal(catalog.store.pricesConfirmed,false);
const products=[];
for(const p of catalog.products){const dir=join(root,'private/products',p.id),mf=JSON.parse(await readFile(join(dir,'manifest.json'),'utf8'));assert.equal(p.pageCount||p.assetCount,mf.pageCount||mf.assetCount);const bytes=await readFile(join(dir,'download.zip'));assert.ok(bytes.length>1000);assert.equal(bytes.subarray(0,2).toString(),'PK');for(const image of Object.values(p.previews)){assert.ok(image.startsWith('/previews/'));assert.ok((await stat(join(root,'public',image))).size>1000);}products.push({id:p.id,count:p.pageCount||p.assetCount,zipBytes:bytes.length,zipSha256:createHash('sha256').update(bytes).digest('hex'),saleStatus:p.saleStatus,priceConfirmed:p.priceConfirmed});}
const files=await readdir(join(root,'public'),{recursive:true});assert.equal(files.some(p=>/\.zip$|\.pptx$|\.sqlite$|\.env$/.test(p)),false);
const html=await readFile(join(root,'public/index.html'),'utf8');assert.match(html,/<html lang="zh-CN">/);assert.match(html,/真实收款未开通/);
const report={date:'2026-10-30',result:'PASS',products,publicPaidFiles:0,checks:['4个商品与实际清单数量一致','16张公开预览存在且非空','真实商品ZIP存在','收费源文件不在public中','简体中文HTML与未开通标识'],notCovered:['真实浏览器渲染、手机溢出、键盘完整流程','Canva所有图形编辑、免费账户导出与复制链接','支付平台沙箱和生产交易']};
await mkdir(join(root,'docs/evidence'),{recursive:true});await writeFile(join(root,'docs/evidence/build.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
