import { createServer } from 'node:http';
import { DatabaseSync } from 'node:sqlite';
import { createHash, createHmac, randomBytes, timingSafeEqual } from 'node:crypto';
import { mkdirSync, readFileSync, realpathSync, lstatSync, openSync, fstatSync, closeSync, createReadStream, constants } from 'node:fs';
import { resolve, join, dirname, relative, sep, extname } from 'node:path';

const SESSION_AGE = 30 * 24 * 60 * 60 * 1000;
const ID = /^[a-z0-9][a-z0-9-]{0,63}$/;
const ORDER_ID = /^ord_[a-f0-9]{32}$/;
const HASH = (value) => createHash('sha256').update(value).digest('hex');
const random = () => randomBytes(32).toString('base64url');
const equals = (a, b) => typeof a === 'string' && typeof b === 'string' && Buffer.byteLength(a) === Buffer.byteLength(b) && timingSafeEqual(Buffer.from(a), Buffer.from(b));
class HttpError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}
const fail = (status, message) => { throw new HttpError(status, message); };

// 公开元数据使用明确字段名单，避免商品制作时误加的管理链接和路径进入接口。
const PRODUCT_KEYS = ['id', 'title', 'category', 'subtitle', 'description', 'pageCount', 'assetCount', 'formats', 'dimensions', 'editable', 'requirements', 'included', 'suggestedPriceMinor', 'currency', 'priceConfirmed', 'fulfillmentReady', 'saleStatus', 'templateLinkVerified'];
const STORE_KEYS = ['name', 'workingName', 'pricesConfirmed', 'description', 'contactEmail', 'region', 'currency', 'policiesConfirmed'];
function publicProduct(product) {
  const result = Object.fromEntries(PRODUCT_KEYS.filter((key) => key in product).map((key) => [key, product[key]]));
  result.previews = Object.fromEntries(Object.entries(product.previews || {}).filter(([key, value]) => ['cover', 'overview', 'detail', 'usage'].includes(key) && typeof value === 'string' && /^\/?previews\/[a-z0-9-]+\/[a-z0-9-]+\.png$/.test(value)));
  return result;
}

function paymentConfig(env) {
  const mode = ['test', 'live'].includes(env.PAYMENTS_MODE) ? env.PAYMENTS_MODE : 'disabled';
  const key = env.STRIPE_SECRET_KEY || '';
  const webhook = env.STRIPE_WEBHOOK_SECRET || '';
  let reason = '在线收款尚未开通，不能创建付款订单。';
  let enabled = false;
  if (mode === 'test') {
    enabled = key.startsWith('sk_test_') && webhook.startsWith('whsec_');
    reason = enabled ? '测试模式：采用建议价，必须通过平台测试付款及已验证回调才会授权。' : '测试收款配置不完整，不能创建付款订单。';
  }
  if (mode === 'live') {
    enabled = key.startsWith('sk_live_') && webhook.startsWith('whsec_') &&
      env.LIVE_PAYMENTS_APPROVED === 'yes' && env.LIVE_POLICIES_CONFIRMED === 'yes' &&
      Boolean(env.LIVE_REGION?.trim()) && /^[A-Z]{3}$/.test(env.LIVE_CURRENCY || '') &&
      Boolean(env.LIVE_REVIEW_REFERENCE?.trim()) && /^https:\/\//.test(env.BASE_URL || '');
    reason = enabled ? '正式收款审核配置已由经营者显式启用，商品仍需单独通过售价与交付校验。' : '正式收款未完成地区、币种、定价与政策审核，服务保持禁止结算。';
  }
  return { mode, enabled, reason, key, webhook };
}

export function createStoreServer(options = {}) {
  const rootDir = resolve(options.rootDir || process.cwd());
  const env = options.env || process.env;
  const config = paymentConfig(env);
  const origin = new URL(env.BASE_URL || `http://127.0.0.1:${env.PORT || 8094}`).origin;
  const secureCookie = origin.startsWith('https:');
  const stripeFetch = options.stripeFetch || globalThis.fetch;
  const now = options.now || Date.now;
  const dbPath = options.dbPath || join(rootDir, 'private/store.sqlite');
  mkdirSync(dirname(dbPath), { recursive: true });
  const db = new DatabaseSync(dbPath);
  db.exec(`PRAGMA foreign_keys=ON; PRAGMA busy_timeout=5000; PRAGMA journal_mode=WAL;
    CREATE TABLE IF NOT EXISTS sessions(id_hash TEXT PRIMARY KEY, csrf TEXT NOT NULL, expires_at INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS orders(
      id TEXT PRIMARY KEY, owner_hash TEXT NOT NULL, request_key TEXT NOT NULL, fingerprint TEXT NOT NULL,
      items_json TEXT NOT NULL, amount INTEGER NOT NULL, currency TEXT NOT NULL, payment_mode TEXT NOT NULL,
      status TEXT NOT NULL, verified_paid INTEGER NOT NULL DEFAULT 0, stripe_session_id TEXT UNIQUE,
      checkout_url TEXT, recovery_hash TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
      UNIQUE(owner_hash, request_key));
    CREATE TABLE IF NOT EXISTS order_sessions(order_id TEXT NOT NULL REFERENCES orders(id), session_hash TEXT NOT NULL,
      PRIMARY KEY(order_id, session_hash));
    CREATE TABLE IF NOT EXISTS webhook_events(id TEXT PRIMARY KEY, order_id TEXT NOT NULL REFERENCES orders(id), event_type TEXT NOT NULL, received_at INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS recovery_attempts(bucket TEXT NOT NULL, attempted_at INTEGER NOT NULL);
    CREATE INDEX IF NOT EXISTS recovery_rate ON recovery_attempts(bucket, attempted_at);
    CREATE TABLE IF NOT EXISTS live_approvals(config_hash TEXT PRIMARY KEY, region TEXT NOT NULL, currency TEXT NOT NULL, review_reference TEXT NOT NULL, recorded_at INTEGER NOT NULL);`);
  if (config.mode === 'live' && config.enabled) {
    const evidence = [env.LIVE_REGION, env.LIVE_CURRENCY, env.LIVE_REVIEW_REFERENCE];
    db.prepare('INSERT OR IGNORE INTO live_approvals VALUES(?,?,?,?,?)').run(HASH(JSON.stringify(evidence)), ...evidence, now());
  }
  const inflight = new Map();

  function safeFile(subpath) {
    const root = realpathSync(rootDir);
    const target = resolve(root, subpath);
    const rel = relative(root, target);
    if (!rel || rel.startsWith(`..${sep}`) || rel === '..' || resolve(target) === root) fail(404, '文件不存在。');
    let current = root;
    for (const segment of rel.split(sep)) {
      current = join(current, segment);
      if (lstatSync(current).isSymbolicLink()) fail(404, '文件不存在。');
    }
    if (!lstatSync(target).isFile()) fail(404, '文件不存在。');
    return target;
  }
  function catalog() {
    let raw;
    try { raw = JSON.parse(readFileSync(safeFile('public/catalog.json'), 'utf8')); }
    catch { fail(503, '商品目录暂不可用。'); }
    const products = Array.isArray(raw) ? raw : raw.products;
    if (!Array.isArray(products)) fail(503, '商品目录格式不正确。');
    const ids = new Set();
    for (const p of products) {
      if (!p || !ID.test(p.id) || ids.has(p.id)) fail(503, '商品目录编号不正确。');
      ids.add(p.id);
    }
    const store = Object.fromEntries(STORE_KEYS.filter((key) => raw.store && key in raw.store).map((key) => [key, raw.store[key]]));
    return { products, store };
  }
  function paymentPublic() {
    return { mode: config.mode, checkoutAvailable: config.enabled, message: config.reason, provider: 'stripe-candidate', isTest: config.mode === 'test' };
  }
  function session(req, res, create = false) {
    const cookie = String(req.headers.cookie || '').split(';').map((v) => v.trim()).find((v) => v.startsWith('xuwu_sid='));
    const sid = cookie?.slice('xuwu_sid='.length);
    if (sid && /^[A-Za-z0-9_-]{43}$/.test(sid)) {
      const stored = db.prepare('SELECT * FROM sessions WHERE id_hash=? AND expires_at>?').get(HASH(sid), now());
      if (stored) return stored;
    }
    if (!create) fail(403, '请先建立商城会话。');
    const token = random();
    const value = { id_hash: HASH(token), csrf: random(), expires_at: now() + SESSION_AGE };
    db.prepare('INSERT INTO sessions VALUES(?,?,?)').run(value.id_hash, value.csrf, value.expires_at);
    res.setHeader('Set-Cookie', `xuwu_sid=${token}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${SESSION_AGE / 1000}${secureCookie ? '; Secure' : ''}`);
    return value;
  }
  function authorizedOrder(id, s) {
    if (!ORDER_ID.test(id || '')) fail(400, '订单编号不正确。');
    const order = db.prepare('SELECT o.* FROM orders o JOIN order_sessions a ON a.order_id=o.id WHERE o.id=? AND a.session_hash=?').get(id, s.id_hash);
    if (!order) fail(403, '当前会话没有此订单的访问权限。');
    return order;
  }
  function publicOrder(order) {
    const canDownload = order.status === 'PAID' && order.verified_paid === 1;
    return {
      id: order.id, status: order.status,
      items: JSON.parse(order.items_json).map((item) => ({ ...item, ...(canDownload ? { downloadUrl: `/api/download/${item.id}?order=${order.id}` } : {}) })),
      amountMinor: order.amount, currency: order.currency.toUpperCase(), paymentMode: order.payment_mode,
      createdAt: new Date(order.created_at).toISOString(), updatedAt: new Date(order.updated_at).toISOString(),
      checkoutUrl: ['PENDING', 'CREATING'].includes(order.status) ? order.checkout_url : null, canDownload,
    };
  }
  function json(res, status, value) {
    res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
    res.end(JSON.stringify(value));
  }
  async function readBody(req, limit = 32_768) {
    const size = Number(req.headers['content-length']);
    if (size > limit) fail(413, '请求内容过大。');
    const chunks = []; let length = 0;
    for await (const chunk of req) {
      length += chunk.length;
      if (length > limit) fail(413, '请求内容过大。');
      chunks.push(chunk);
    }
    return Buffer.concat(chunks);
  }
  async function bodyJson(req) {
    if (!/^application\/json(?:\s*;|$)/i.test(req.headers['content-type'] || '')) fail(415, '请使用JSON请求。');
    let body;
    try { body = JSON.parse((await readBody(req)).toString('utf8')); }
    catch (error) { if (error instanceof HttpError) throw error; fail(400, 'JSON内容不正确。'); }
    if (!body || typeof body !== 'object' || Array.isArray(body)) fail(400, '请求内容不正确。');
    return body;
  }
  function onlyKeys(body, keys) {
    if (Object.keys(body).some((key) => !keys.includes(key))) fail(400, '请求包含不允许的字段。');
  }
  function csrf(req, s) {
    if (req.headers.origin !== origin || req.headers['sec-fetch-site'] === 'cross-site' || !equals(req.headers['x-csrf-token'], s.csrf)) fail(403, '同源或会话校验失败，请刷新后重试。');
  }
  async function stripe(path, { form, idempotencyKey } = {}) {
    let response;
    try {
      response = await stripeFetch(`https://api.stripe.com/v1${path}`, {
        method: form ? 'POST' : 'GET',
        headers: { Authorization: `Bearer ${config.key}`, 'Stripe-Version': env.STRIPE_API_VERSION || '2026-10-28.endive',
          ...(form ? { 'Content-Type': 'application/x-www-form-urlencoded', 'Idempotency-Key': idempotencyKey } : {}) },
        ...(form ? { body: form.toString() } : {}), signal: AbortSignal.timeout(15_000),
      });
    } catch { fail(502, '支付平台暂不可用，请稍后重试。'); }
    if (!response.ok) fail(502, '支付平台请求未完成，请稍后重试。');
    try { return await response.json(); } catch { fail(502, '支付平台响应不正确。'); }
  }
  function purchasable(ids) {
    const { products, store } = catalog();
    const items = ids.map((id) => {
      const p = products.find((entry) => entry.id === id);
      if (!p) fail(400, '商品不存在。');
      if (p.fulfillmentReady !== true || p.saleStatus !== 'ready') fail(409, '商品尚未完成交付核验，暂不能结算。');
      if ((p.requiresCanvaTemplate === true || p.formats?.some((format) => /canva/i.test(format))) && p.templateLinkVerified !== true) fail(409, '商品的买家模板复制链接尚未核验。');
      if (config.mode === 'live' && (p.priceConfirmed !== true || store.pricesConfirmed !== true || p.currency?.toUpperCase() !== env.LIVE_CURRENCY)) fail(409, '商品正式售价或币种尚未审核。');
      if (!Number.isSafeInteger(p.suggestedPriceMinor) || p.suggestedPriceMinor < 1 || p.suggestedPriceMinor > 10_000_000 || !/^[a-z]{3}$/i.test(p.currency || '') || typeof p.title !== 'string' || p.title.length < 1 || p.title.length > 200) fail(409, '商品价格、币种或标题不正确。');
      try { safeFile(`private/products/${id}/download.zip`); } catch { fail(409, '商品交付文件尚未就绪。'); }
      return { id, title: p.title, unitAmountMinor: p.suggestedPriceMinor, currency: p.currency.toLowerCase(), quantity: 1 };
    });
    if (new Set(items.map((p) => p.currency)).size !== 1) fail(400, '同一订单只能使用一种币种。');
    return items;
  }
  async function makeCheckout(order) {
    if (inflight.has(order.id)) return inflight.get(order.id);
    const work = (async () => {
      const form = new URLSearchParams({ mode: 'payment', success_url: `${origin}/#/order?id=${order.id}`, cancel_url: `${origin}/#/order?id=${order.id}&cancelled=1`,
        'metadata[store_order_id]': order.id, 'metadata[store_session_id]': order.owner_hash });
      JSON.parse(order.items_json).forEach((item, i) => {
        form.set(`line_items[${i}][quantity]`, '1');
        form.set(`line_items[${i}][price_data][currency]`, item.currency);
        form.set(`line_items[${i}][price_data][unit_amount]`, String(item.unitAmountMinor));
        form.set(`line_items[${i}][price_data][product_data][name]`, item.title);
        form.set(`line_items[${i}][price_data][product_data][metadata][store_product_id]`, item.id);
      });
      try {
        const remote = await stripe('/checkout/sessions', { form, idempotencyKey: `xuwu-${order.id}` });
        if (!/^cs_[a-zA-Z0-9_]+$/.test(remote.id || '') || remote.livemode !== (order.payment_mode === 'live') || remote.mode !== 'payment' || remote.metadata?.store_order_id !== order.id || remote.metadata?.store_session_id !== order.owner_hash) fail(502, '支付平台结算绑定不正确。');
        let url;
        try { url = new URL(remote.url); } catch { fail(502, '支付平台跳转地址不正确。'); }
        if (url.protocol !== 'https:' || url.hostname !== 'checkout.stripe.com' || url.username || url.password) fail(502, '支付平台跳转地址不正确。');
        db.prepare('UPDATE orders SET stripe_session_id=?, checkout_url=?, status=CASE WHEN verified_paid=1 THEN status ELSE ? END,updated_at=? WHERE id=?').run(remote.id, remote.url, 'PENDING', now(), order.id);
      } catch (error) {
        db.prepare("UPDATE orders SET status='CREATION_FAILED',updated_at=? WHERE id=? AND verified_paid=0 AND stripe_session_id IS NULL").run(now(), order.id);
        throw error;
      }
      return db.prepare('SELECT * FROM orders WHERE id=?').get(order.id);
    })();
    inflight.set(order.id, work);
    try { return await work; } finally { inflight.delete(order.id); }
  }
  async function checkout(req, res, s) {
    const body = await bodyJson(req); onlyKeys(body, ['items', 'requestKey']);
    if (!Array.isArray(body.items) || body.items.length < 1 || body.items.length > 20 || body.items.some((id) => typeof id !== 'string' || !ID.test(id)) || new Set(body.items).size !== body.items.length || !/^[A-Za-z0-9_-]{8,100}$/.test(body.requestKey || '')) fail(400, '请检查商品编号、数量和请求编号。');
    if (!config.enabled) fail(503, config.reason);
    const ids = [...body.items].sort(); const fingerprint = HASH(JSON.stringify(ids));
    let order = db.prepare('SELECT * FROM orders WHERE owner_hash=? AND request_key=?').get(s.id_hash, body.requestKey);
    let recoveryCode; let created = false;
    if (order && order.fingerprint !== fingerprint) fail(409, '相同请求编号不能用于不同商品，请重新发起。');
    if (order && order.payment_mode !== config.mode) fail(409, '支付模式已变更，请使用新的请求编号重新结算。');
    if (!order) {
      const items = purchasable(ids); recoveryCode = random(); created = true;
      const id = `ord_${randomBytes(16).toString('hex')}`; const time = now();
      db.exec('BEGIN IMMEDIATE');
      try {
        db.prepare('INSERT INTO orders(id,owner_hash,request_key,fingerprint,items_json,amount,currency,payment_mode,status,recovery_hash,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)')
          .run(id, s.id_hash, body.requestKey, fingerprint, JSON.stringify(items), items.reduce((n, p) => n + p.unitAmountMinor, 0), items[0].currency, config.mode, 'CREATING', HASH(recoveryCode), time, time);
        db.prepare('INSERT INTO order_sessions VALUES(?,?)').run(id, s.id_hash); db.exec('COMMIT');
      } catch (error) { db.exec('ROLLBACK'); throw error; }
      order = db.prepare('SELECT * FROM orders WHERE id=?').get(id);
    }
    if (['CREATING', 'CREATION_FAILED'].includes(order.status)) order = await makeCheckout(order);
    json(res, created ? 201 : 200, { order: publicOrder(order), checkoutUrl: order.checkout_url, ...(recoveryCode ? { recoveryCode } : {}) });
  }
  async function recover(req, res, s) {
    const body = await bodyJson(req); onlyKeys(body, ['orderId', 'recoveryCode']);
    if (!ORDER_ID.test(body.orderId || '') || typeof body.recoveryCode !== 'string' || body.recoveryCode.length > 128) fail(400, '订单编号或恢复凭据格式不正确。');
    const ip = HASH(req.socket.remoteAddress || 'unknown'); const buckets = [`ip:${ip}`, `session:${s.id_hash}`];
    db.prepare('DELETE FROM recovery_attempts WHERE attempted_at<?').run(now() - 15 * 60_000);
    for (const bucket of buckets) {
      const count = db.prepare('SELECT count(*) AS n FROM recovery_attempts WHERE bucket=? AND attempted_at>?').get(bucket, now() - 15 * 60_000).n;
      if (count >= 10) fail(429, '恢复尝试过多，请15分钟后重试。');
    }
    for (const bucket of buckets) db.prepare('INSERT INTO recovery_attempts VALUES(?,?)').run(bucket, now());
    const order = db.prepare('SELECT * FROM orders WHERE id=?').get(body.orderId);
    if (!order || !equals(order.recovery_hash, HASH(body.recoveryCode))) fail(403, '订单编号或恢复凭据不正确。');
    db.prepare('INSERT OR IGNORE INTO order_sessions VALUES(?,?)').run(order.id, s.id_hash);
    json(res, 200, { order: publicOrder(order) });
  }
  function verifySignature(raw, header) {
    const parts = String(header || '').split(',').map((part) => part.trim().split('='));
    const times = parts.filter(([key]) => key === 't').map(([, value]) => value);
    if (times.length !== 1 || !/^\d{1,12}$/.test(times[0])) fail(400, '支付通知签名不正确。');
    const timestamp = Number(times[0]);
    if (Math.abs(now() / 1000 - timestamp) > 300) fail(400, '支付通知签名已过期。');
    const expected = createHmac('sha256', config.webhook).update(`${times[0]}.`).update(raw).digest('hex');
    if (!parts.some(([key, signature]) => key === 'v1' && /^[a-fA-F0-9]{64}$/.test(signature || '') && equals(signature.toLowerCase(), expected))) fail(400, '支付通知签名不正确。');
  }
  function validateRemote(order, remote, lines, event) {
    if (remote.id !== order.stripe_session_id || remote.mode !== 'payment' || remote.livemode !== (order.payment_mode === 'live') || event.livemode !== remote.livemode || remote.metadata?.store_order_id !== order.id || remote.metadata?.store_session_id !== order.owner_hash || remote.currency !== order.currency || remote.amount_total !== order.amount) fail(409, '支付平台订单核验未通过。');
    const expected = JSON.parse(order.items_json);
    if (lines.has_more !== false || !Array.isArray(lines.data) || lines.data.length !== expected.length) fail(409, '支付平台商品清单核验未通过。');
    const found = new Set();
    for (const line of lines.data) {
      const id = line.price?.product?.metadata?.store_product_id;
      const item = expected.find((p) => p.id === id);
      if (!item || found.has(id) || line.quantity !== 1 || line.currency !== item.currency || line.amount_total !== item.unitAmountMinor || line.amount_subtotal !== item.unitAmountMinor || line.price?.unit_amount !== item.unitAmountMinor || line.price?.currency !== item.currency || line.price?.livemode !== remote.livemode) fail(409, '支付平台商品价格、币种或数量核验未通过。');
      found.add(id);
    }
  }
  async function webhook(req, res) {
    if (!config.enabled) fail(503, '支付通知接入尚未启用。');
    const raw = await readBody(req, 262_144); verifySignature(raw, req.headers['stripe-signature']);
    let event;
    try { event = JSON.parse(raw.toString('utf8')); } catch { fail(400, '支付通知JSON不正确。'); }
    if (!/^evt_[A-Za-z0-9_.-]{1,200}$/.test(event.id || '')) fail(400, '支付通知编号不正确。');
    const types = ['checkout.session.completed', 'checkout.session.async_payment_succeeded', 'checkout.session.async_payment_failed', 'checkout.session.expired'];
    if (!types.includes(event.type)) return json(res, 200, { received: true, ignored: true });
    if (db.prepare('SELECT 1 FROM webhook_events WHERE id=?').get(event.id)) return json(res, 200, { received: true, duplicate: true });
    const remoteId = event.data?.object?.id;
    if (!/^cs_[a-zA-Z0-9_]+$/.test(remoteId || '')) fail(400, '支付通知会话不正确。');
    const order = db.prepare('SELECT * FROM orders WHERE stripe_session_id=?').get(remoteId);
    if (!order || order.payment_mode !== config.mode) fail(409, '支付通知没有匹配的订单。');
    const remote = await stripe(`/checkout/sessions/${encodeURIComponent(remoteId)}`);
    const lines = await stripe(`/checkout/sessions/${encodeURIComponent(remoteId)}/line_items?limit=100&expand%5B%5D=data.price.product`);
    validateRemote(order, remote, lines, event);
    let status;
    if (remote.payment_status === 'paid' && remote.status === 'complete') status = 'PAID';
    else if (remote.payment_status !== 'unpaid') fail(409, '支付平台付款状态不正确。');
    else if (event.type === 'checkout.session.async_payment_succeeded') fail(409, '平台尚未确认异步付款到账。');
    else if (event.type === 'checkout.session.async_payment_failed') status = 'FAILED';
    else if (event.type === 'checkout.session.expired') {
      if (remote.status !== 'expired') fail(409, '支付平台会话尚未过期。');
      status = 'EXPIRED';
    } else status = 'PENDING';
    db.exec('BEGIN IMMEDIATE');
    try {
      const inserted = db.prepare('INSERT OR IGNORE INTO webhook_events VALUES(?,?,?,?)').run(event.id, order.id, event.type, now());
      if (inserted.changes) {
        // 只有通过签名和平台双重核验的PAID可以授权；迟到的待付/失败不能撤销已付。
        db.prepare("UPDATE orders SET status=CASE WHEN verified_paid=1 THEN status WHEN ?='PENDING' AND status IN ('FAILED','EXPIRED') THEN status ELSE ? END, verified_paid=CASE WHEN ?='PAID' THEN 1 ELSE verified_paid END, updated_at=? WHERE id=?").run(status, status, status, now(), order.id);
      }
      db.exec('COMMIT');
    } catch (error) { db.exec('ROLLBACK'); throw error; }
    json(res, 200, { received: true });
  }
  function sendFile(req, res, file, downloadName) {
    const fd = openSync(file, constants.O_RDONLY | (constants.O_NOFOLLOW || 0));
    const stat = fstatSync(fd);
    if (!stat.isFile()) { closeSync(fd); fail(404, '文件不存在。'); }
    const mime = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml' };
    res.writeHead(200, { 'Content-Type': downloadName ? 'application/zip' : mime[extname(file)] || 'application/octet-stream', 'Content-Length': stat.size, 'Cache-Control': downloadName ? 'no-store' : 'no-cache', ...(downloadName ? { 'Content-Disposition': `attachment; filename="${downloadName}"` } : {}) });
    if (req.method === 'HEAD') { closeSync(fd); res.end(); } else {
      const stream = createReadStream(file, { fd, autoClose: true });
      stream.on('error', () => res.destroy());
      res.on('close', () => stream.destroy()); stream.pipe(res);
    }
  }
  const server = createServer(async (req, res) => {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Referrer-Policy', 'same-origin');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
    try {
      let url;
      try { url = new URL(req.url, origin); } catch { fail(400, '请求地址不正确。'); }
      const path = url.pathname;
      if (path === '/api/webhooks/stripe' && req.method === 'POST') return await webhook(req, res);
      if (path === '/api/catalog' && req.method === 'GET' || path === '/catalog.json' && ['GET', 'HEAD'].includes(req.method)) {
        const data = catalog(); return json(res, 200, { products: data.products.map(publicProduct), store: data.store, payment: paymentPublic() });
      }
      if (path === '/api/session' && req.method === 'GET') return json(res, 200, { csrfToken: session(req, res, true).csrf });
      if (path.startsWith('/api/')) {
        const s = session(req, res);
        if (req.method === 'POST') csrf(req, s);
        if (path === '/api/checkout' && req.method === 'POST') return await checkout(req, res, s);
        if (path === '/api/order/recover' && req.method === 'POST') return await recover(req, res, s);
        if (path === '/api/orders' && req.method === 'GET') {
          const orders = db.prepare('SELECT o.* FROM orders o JOIN order_sessions a ON a.order_id=o.id WHERE a.session_hash=? ORDER BY o.created_at DESC').all(s.id_hash);
          return json(res, 200, { orders: orders.map(publicOrder) });
        }
        if (path === '/api/order' && req.method === 'GET') return json(res, 200, { order: publicOrder(authorizedOrder(url.searchParams.get('id'), s)) });
        const match = /^\/api\/download\/([a-z0-9-]+)$/.exec(path);
        if (match && ['GET', 'HEAD'].includes(req.method)) {
          const order = authorizedOrder(url.searchParams.get('order'), s);
          if (order.status !== 'PAID' || order.verified_paid !== 1 || !JSON.parse(order.items_json).some((p) => p.id === match[1])) fail(403, '付款尚未核实或订单未包含此商品，不能下载。');
          let file;
          try { file = safeFile(`private/products/${match[1]}/download.zip`); } catch { fail(404, '交付文件暂不可用，请联系经营者。'); }
          return sendFile(req, res, file, `${match[1]}.zip`);
        }
        fail(404, '接口不存在。');
      }
      if (!['GET', 'HEAD'].includes(req.method)) fail(405, '请求方法不允许。');
      const staticFiles = new Set(['/index.html', '/styles.css', '/app.js', '/shop-model.mjs', '/favicon.svg']);
      const staticPath = path === '/' ? '/index.html' : path;
      if (!staticFiles.has(staticPath) && !/^\/previews\/[a-z0-9-]+\/[a-z0-9-]+\.png$/.test(staticPath)) fail(404, '文件不存在。');
      let file;
      try { file = safeFile(`public${staticPath}`); } catch { fail(404, '文件不存在。'); }
      sendFile(req, res, file);
    } catch (error) {
      if (res.headersSent) return res.destroy();
      json(res, error instanceof HttpError ? error.status : 500, { message: error instanceof HttpError ? error.message : '服务暂不可用，请稍后重试。' });
    }
  });
  server.requestTimeout = 30_000;
  server.headersTimeout = 15_000;
  return { server, close: async () => { await new Promise((resolveClose, reject) => server.close((error) => error ? reject(error) : resolveClose())); db.close(); } };
}
