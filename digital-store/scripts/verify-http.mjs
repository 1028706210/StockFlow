import assert from 'node:assert/strict';import {mkdir,writeFile} from 'node:fs/promises';import {dirname,resolve,join} from 'node:path';import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');const base=process.env.BASE_URL||'http://127.0.0.1:8094';
const page=await fetch(base+'/');assert.equal(page.status,200);assert.match(await page.text(),/真实收款未开通/);
const data=await (await fetch(base+'/api/catalog')).json();assert.equal(data.products.length,4);assert.equal(data.payment.checkoutAvailable,false);
let images=0;for(const p of data.products)for(const path of Object.values(p.previews)){const response=await fetch(base+path);assert.equal(response.status,200);assert.match(response.headers.get('content-type'),/image\/png/);images++;}
for(const path of ['/app.js','/shop-model.mjs','/styles.css','/favicon.svg'])assert.equal((await fetch(base+path)).status,200,path);
for(const path of ['/private/products/c-icons/download.zip','/private/canva-designs.json','/.env','/server/store.mjs','/previews/../../private/products/c-icons/download.zip'])assert.equal((await fetch(base+path)).status,404,path);
assert.equal((await fetch(base+'/api/download/c-icons?order=ord_'+'1'.repeat(32))).status,403);
const response=await fetch(base+'/api/session'),{csrfToken}=await response.json(),cookie=response.headers.get('set-cookie').split(';')[0];
const orders=await (await fetch(base+'/api/orders',{headers:{Cookie:cookie}})).json();assert.deepEqual(orders.orders,[]);
assert.equal((await fetch(base+'/api/checkout',{method:'POST',headers:{Origin:base,Cookie:cookie,'Content-Type':'application/json','X-CSRF-Token':csrfToken},body:JSON.stringify({items:['c-icons'],requestKey:'verify-disabled-checkout'})})).status,503);
assert.deepEqual((await (await fetch(base+'/api/orders',{headers:{Cookie:cookie}})).json()).orders,[]);
const report={date:'2026-10-30',base,result:'PASS',pageStatus:200,productCount:4,imageResponses:images,publicCodeResponses:4,privatePaths:'404',unpaidDownload:'403',disabledCheckout:'503',fakeOrdersCreated:0,visualBrowserAcceptance:false};
await mkdir(join(root,'docs/evidence'),{recursive:true});await writeFile(join(root,'docs/evidence/http.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
