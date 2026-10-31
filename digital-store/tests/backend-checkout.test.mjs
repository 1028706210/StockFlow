import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm, symlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createHmac } from 'node:crypto';
import { createStoreServer } from '../server/store.mjs';

// 本地签名回调演练：只替换外部 Stripe HTTP，SQLite、HTTP、会话和文件均实际执行。
async function fixture(t, mode = 'test') {
  const rootDir = await mkdtemp(join(tmpdir(), 'xu-wu-backend-'));
  await mkdir(join(rootDir, 'public'), { recursive: true });
  await mkdir(join(rootDir, 'private/products/c-icons'), { recursive: true });
  const products = [
    { id: 'c-icons', title: '原创图标', suggestedPriceMinor: 1900, currency: 'CNY', priceConfirmed: false, fulfillmentReady: true, saleStatus: 'ready', included: ['12个SVG'], canvaManagementUrl: 'PRIVATE-MANAGEMENT-LINK' },
    { id: 'a-social', title: '社交模板', suggestedPriceMinor: 4900, currency: 'CNY', priceConfirmed: false, fulfillmentReady: false, saleStatus: 'blocked' },
  ];
  await writeFile(join(rootDir, 'public/catalog.json'), JSON.stringify({ store: { name: '序物设计', pricesConfirmed: false }, products }));
  await writeFile(join(rootDir, 'public/index.html'), '<h1>商城</h1>');
  await writeFile(join(rootDir, 'private/products/c-icons/download.zip'), Buffer.from('PK\u0003\u0004本地演练文件'));
  const sessions = new Map();
  let alterSession = (s) => s;
  let alterLines = (l) => l;
  let createCount = 0; let failCreate = false;
  const stripeFetch = async (url, init = {}) => {
    const path = new URL(url).pathname;
    assert.equal(new URL(url).origin, 'https://api.stripe.com');
    if (init.method === 'POST' && path === '/v1/checkout/sessions') {
      if (failCreate) throw new Error('仅本地演练的网络失败');
      const p = new URLSearchParams(init.body);
      assert.equal(p.get('mode'), 'payment');
      assert.equal(p.get('line_items[0][quantity]'), '1');
      assert.equal(p.get('line_items[0][price_data][unit_amount]'), '1900');
      const id = `cs_test_local_${++createCount}`;
      const session = { id, object: 'checkout.session', mode: 'payment', livemode: false, payment_status: 'unpaid', status: 'open', currency: 'cny', amount_total: 1900,
        metadata: { store_order_id: p.get('metadata[store_order_id]'), store_session_id: p.get('metadata[store_session_id]') },
        url: `https://checkout.stripe.com/c/pay/${id}` };
      sessions.set(id, session);
      return Response.json(session);
    }
    const id = path.split('/')[4];
    if (!sessions.has(id)) return Response.json({ error: {} }, { status: 404 });
    if (path.endsWith('/line_items')) return Response.json(alterLines({ object: 'list', has_more: false, data: [
      { id: 'li_local', object: 'item', quantity: 1, currency: 'cny', amount_subtotal: 1900, amount_total: 1900,
        price: { id: 'price_local', currency: 'cny', unit_amount: 1900, livemode: false, product: { id: 'prod_local', metadata: { store_product_id: 'c-icons' } } } },
    ] }));
    return Response.json(alterSession(structuredClone(sessions.get(id))));
  };
  const env = { PAYMENTS_MODE: mode, STRIPE_SECRET_KEY: 'sk_test_local_fixture_only', STRIPE_WEBHOOK_SECRET: 'whsec_local_fixture_only', BASE_URL: 'http://127.0.0.1:8094' };
  let app;
  let base;
  async function start() {
    app = createStoreServer({ rootDir, dbPath: join(rootDir, 'private/store.sqlite'), env, stripeFetch });
    await new Promise((resolve) => app.server.listen(0, '127.0.0.1', resolve));
    base = `http://127.0.0.1:${app.server.address().port}`;
  }
  await start();
  t.after(async () => { await app.close(); await rm(rootDir, { recursive: true, force: true }); });
  const client = async () => {
    const res = await fetch(`${base}/api/session`);
    const { csrfToken } = await res.json();
    const cookie = res.headers.get('set-cookie').split(';')[0];
    return {
      cookie, csrfToken,
      get: (path) => fetch(`${base}${path}`, { headers: { Cookie: cookie } }),
      post: (path, body, extra = {}) => fetch(`${base}${path}`, { method: 'POST', headers: { Cookie: cookie, Origin: env.BASE_URL, 'Content-Type': 'application/json', 'X-CSRF-Token': csrfToken, ...extra }, body: JSON.stringify(body) }),
    };
  };
  async function checkout(c, key = 'request-local-0001') {
    const r = await c.post('/api/checkout', { items: ['c-icons'], requestKey: key });
    assert.equal(r.status, 201);
    return r.json();
  }
  async function event(order, type = 'checkout.session.completed', { timestamp = Math.floor(Date.now() / 1000), bad = false, eventId = `evt_${type}_${order.id}` } = {}) {
    const session = [...sessions.values()].find((s) => s.metadata.store_order_id === order.id);
    const raw = JSON.stringify({ id: eventId, type, livemode: false, data: { object: { id: session.id, metadata: session.metadata } } });
    const signature = createHmac('sha256', env.STRIPE_WEBHOOK_SECRET).update(`${timestamp}.${raw}`).digest('hex');
    return fetch(`${base}/api/webhooks/stripe`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Stripe-Signature': `t=${timestamp},v1=${'0'.repeat(64)},v1=${bad ? '1'.repeat(64) : signature}` }, body: raw });
  }
  function paid(order) {
    const session = [...sessions.values()].find((s) => s.metadata.store_order_id === order.id);
    session.payment_status = 'paid'; session.status = 'complete';
  }
  return { rootDir, env, client, checkout, event, paid, sessions, failCreate: (value) => { failCreate = value; }, alterSession: (f) => { alterSession = f; }, alterLines: (f) => { alterLines = f; }, restart: async () => { await app.close(); await start(); }, base: () => base, createCount: () => createCount };
}

test('默认关闭支付并隐藏私有路径及管理链接', async (t) => {
  const f = await fixture(t, 'disabled');
  const c = await f.client();
  const catalog = await (await c.get('/api/catalog')).json();
  assert.equal(catalog.payment.checkoutAvailable, false);
  assert.equal(JSON.stringify(catalog).includes('PRIVATE-MANAGEMENT-LINK'), false);
  assert.equal((await c.post('/api/checkout', { items: ['c-icons'], requestKey: 'disabled-0001' })).status, 503);
  assert.deepEqual((await (await c.get('/api/orders')).json()).orders, []);
  for (const path of ['/private/products/c-icons/download.zip', '/server/store.mjs', '/.env', '/catalog.json']) {
    const res = await c.get(path);
    assert.equal(path === '/catalog.json' ? (await res.text()).includes('PRIVATE-MANAGEMENT-LINK') : res.status === 404, path === '/catalog.json' ? false : true);
  }
});

test('会话CSRF、同源、重复商品和未就绪商品校验', async (t) => {
  const f = await fixture(t); const c = await f.client();
  const body = { items: ['c-icons'], requestKey: 'request-csrf-0001' };
  assert.equal((await c.post('/api/checkout', body, { 'X-CSRF-Token': 'wrong' })).status, 403);
  assert.equal((await c.post('/api/checkout', body, { Origin: 'https://evil.example' })).status, 403);
  assert.equal((await c.post('/api/checkout', { ...body, items: ['c-icons', 'c-icons'] })).status, 400);
  assert.equal((await c.post('/api/checkout', { ...body, items: ['a-social'] })).status, 409);
  assert.equal((await c.post('/api/checkout', { ...body, amount: 1 })).status, 400);
});

test('结算幂等、未付拒绝、跨会话拒绝、刷新和重启保留订单', async (t) => {
  const f = await fixture(t); const c = await f.client(); const stranger = await f.client();
  const result = await f.checkout(c); const { order } = result;
  assert.equal(order.amountMinor, 1900); assert.equal(order.status, 'PENDING');
  assert.equal((await c.get(`/api/download/c-icons?order=${order.id}`)).status, 403);
  assert.equal((await stranger.get(`/api/order?id=${order.id}`)).status, 403);
  assert.equal((await stranger.get(`/api/download/c-icons?order=${order.id}`)).status, 403);
  const again = await c.post('/api/checkout', { items: ['c-icons'], requestKey: 'request-local-0001' });
  assert.equal(again.status, 200); assert.equal((await again.json()).order.id, order.id); assert.equal(f.createCount(), 1);
  assert.equal((await c.post('/api/checkout', { items: ['a-social'], requestKey: 'request-local-0001' })).status, 409);
  await f.restart();
  assert.equal((await (await c.get(`/api/order?id=${order.id}`)).json()).order.status, 'PENDING');
  assert.equal((await (await c.get('/api/orders')).json()).orders.length, 1);
});

test('签名错误及过期通知不能授权，多个v1签名允许有效轮换签名', async (t) => {
  const f = await fixture(t); const c = await f.client(); const { order } = await f.checkout(c); f.paid(order);
  assert.equal((await f.event(order, undefined, { bad: true })).status, 400);
  assert.equal((await f.event(order, undefined, { timestamp: Math.floor(Date.now() / 1000) - 301 })).status, 400);
  assert.equal((await c.get(`/api/download/c-icons?order=${order.id}`)).status, 403);
  assert.equal((await f.event(order)).status, 200);
  const res = await c.get(`/api/download/c-icons?order=${order.id}`);
  assert.equal(res.status, 200); assert.match(res.headers.get('content-disposition'), /attachment/); assert.equal(res.headers.get('cache-control'), 'no-store');
  assert.deepEqual(Buffer.from(await res.arrayBuffer()), Buffer.from('PK\u0003\u0004本地演练文件'));
});

for (const [name, mutate] of [
  ['金额', (f) => f.alterSession((s) => ({ ...s, amount_total: 1 }))],
  ['币种', (f) => f.alterSession((s) => ({ ...s, currency: 'usd' }))],
  ['会话绑定', (f) => f.alterSession((s) => ({ ...s, metadata: { ...s.metadata, store_session_id: 'wrong' } }))],
  ['生产模式', (f) => f.alterSession((s) => ({ ...s, livemode: true }))],
  ['商品SKU', (f) => f.alterLines((l) => { l.data[0].price.product.metadata.store_product_id = 'wrong'; return l; })],
  ['单项价格', (f) => f.alterLines((l) => { l.data[0].price.unit_amount = 1; return l; })],
  ['数量', (f) => f.alterLines((l) => { l.data[0].quantity = 2; return l; })],
]) test(`平台回查${name}不符拒绝授权`, async (t) => {
  const f = await fixture(t); const c = await f.client(); const { order } = await f.checkout(c); f.paid(order); mutate(f);
  assert.equal((await f.event(order)).status, 409);
  assert.equal((await c.get(`/api/download/c-icons?order=${order.id}`)).status, 403);
});

test('异步待付到成功、并发重复通知及迟到事件不会降级已付款', async (t) => {
  const f = await fixture(t); const c = await f.client(); const { order } = await f.checkout(c);
  assert.equal((await f.event(order)).status, 200);
  assert.equal((await (await c.get(`/api/order?id=${order.id}`)).json()).order.status, 'PENDING');
  f.paid(order);
  const results = await Promise.all([f.event(order, 'checkout.session.async_payment_succeeded'), f.event(order, 'checkout.session.async_payment_succeeded')]);
  assert.deepEqual(results.map((r) => r.status), [200, 200]);
  assert.equal((await f.event(order, 'checkout.session.async_payment_failed')).status, 200);
  assert.equal((await (await c.get(`/api/order?id=${order.id}`)).json()).order.status, 'PAID');
  assert.equal((await c.get(`/api/download/c-icons?order=${order.id}`)).status, 200);
  assert.equal((await c.get(`/api/download/d-decor?order=${order.id}`)).status, 403);
});

test('异步失败与过期不发货，跳转URL不能伪造成功', async (t) => {
  const f = await fixture(t); const c = await f.client();
  const { order } = await f.checkout(c);
  assert.equal((await f.event(order, 'checkout.session.async_payment_failed')).status, 200);
  assert.equal((await (await c.get(`/api/order?id=${order.id}&paid=true`)).json()).order.status, 'FAILED');
  assert.equal((await c.get(`/api/download/c-icons?order=${order.id}`)).status, 403);
  const { order: expired } = await f.checkout(c, 'request-expired-0001');
  [...f.sessions.values()].find((s) => s.metadata.store_order_id === expired.id).status = 'expired';
  assert.equal((await f.event(expired, 'checkout.session.expired')).status, 200);
  assert.equal((await (await c.get(`/api/order?id=${expired.id}`)).json()).order.status, 'EXPIRED');
});

test('恢复凭据仅POST恢复授权且受限速，已付订单可跨新会话恢复', async (t) => {
  const f = await fixture(t); const c = await f.client(); const stranger = await f.client();
  const { order, recoveryCode } = await f.checkout(c); assert.ok(recoveryCode.length >= 32); f.paid(order); await f.event(order);
  assert.equal((await stranger.get(`/api/order?id=${order.id}&recoveryCode=${recoveryCode}`)).status, 403);
  assert.equal((await stranger.post('/api/order/recover', { orderId: order.id, recoveryCode: 'wrong' })).status, 403);
  assert.equal((await stranger.post('/api/order/recover', { orderId: order.id, recoveryCode })).status, 200);
  assert.equal((await stranger.get(`/api/download/c-icons?order=${order.id}`)).status, 200);
  for (let i = 0; i < 10; i++) await stranger.post('/api/order/recover', { orderId: order.id, recoveryCode: 'wrong' });
  assert.equal((await stranger.post('/api/order/recover', { orderId: order.id, recoveryCode: 'wrong' })).status, 429);
});

test('测试密钥配置缺失与未审核live配置均无法开通', async (t) => {
  const f = await fixture(t, 'live'); const c = await f.client();
  assert.equal((await (await c.get('/api/catalog')).json()).payment.checkoutAvailable, false);
  assert.equal((await c.post('/api/checkout', { items: ['c-icons'], requestKey: 'request-live-0001' })).status, 503);
});

test('支付创建失败保留订单，同键重试不会凭空发货', async (t) => {
  const f = await fixture(t); const c = await f.client(); f.failCreate(true);
  const body = { items: ['c-icons'], requestKey: 'request-retry-0001' };
  assert.equal((await c.post('/api/checkout', body)).status, 502);
  const orders = (await (await c.get('/api/orders')).json()).orders;
  assert.equal(orders[0].status, 'CREATION_FAILED'); assert.equal(orders[0].canDownload, false);
  f.failCreate(false);
  assert.equal((await c.post('/api/checkout', body)).status, 200);
  assert.equal((await (await c.get('/api/orders')).json()).orders.length, 1);
});

test('缺少测试配置、私有符号链接及过大请求均无法启用交付', async (t) => {
  const f = await fixture(t); f.env.STRIPE_SECRET_KEY = ''; await f.restart(); const c = await f.client();
  assert.equal((await (await c.get('/api/catalog')).json()).payment.checkoutAvailable, false);
  assert.equal((await c.post('/api/checkout', { items: ['c-icons'], requestKey: 'request-no-key-0001' })).status, 503);
  const huge = await c.post('/api/checkout', { items: ['c-icons'], requestKey: 'x'.repeat(40_000) });
  assert.equal(huge.status, 413);
  const secret = join(f.rootDir, 'private/secret-data'); await mkdir(secret);
  await writeFile(join(secret, 'cover.png'), 'PRIVATE');
  await mkdir(join(f.rootDir, 'public/previews'), { recursive: true });
  await symlink(secret, join(f.rootDir, 'public/previews/leak'), 'junction');
  assert.equal((await c.get('/previews/leak/cover.png')).status, 404);
});

test('失败订单不会被迟到的未付通知覆盖', async (t) => {
  const f = await fixture(t); const c = await f.client(); const { order } = await f.checkout(c);
  assert.equal((await f.event(order, 'checkout.session.async_payment_failed')).status, 200);
  assert.equal((await f.event(order, 'checkout.session.completed')).status, 200);
  assert.equal((await (await c.get(`/api/order?id=${order.id}`)).json()).order.status, 'FAILED');
});

test('变更支付模式后不能重新使用旧模式的结算订单', async (t) => {
  const f = await fixture(t); const c = await f.client(); await f.checkout(c);
  Object.assign(f.env, { PAYMENTS_MODE: 'live', STRIPE_SECRET_KEY: 'sk_live_local_fixture_only', LIVE_PAYMENTS_APPROVED: 'yes', LIVE_POLICIES_CONFIRMED: 'yes', LIVE_REGION: '测试经营地区', LIVE_CURRENCY: 'CNY', LIVE_REVIEW_REFERENCE: '仅本地演练', BASE_URL: 'https://store.example.test' });
  await f.restart();
  assert.equal((await c.post('/api/checkout', { items: ['c-icons'], requestKey: 'request-local-0001' })).status, 409);
});
