import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
import { createStoreServer } from './store.mjs';

const rootDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const port = Number(process.env.PORT || 8094);
const host = process.env.HOST || '127.0.0.1';
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('PORT必须是有效端口。');
const app = createStoreServer({ rootDir });
app.server.on('error', (error) => { console.error(`商城服务启动失败：${error.code || '未知错误'}`); process.exitCode = 1; });
app.server.listen(port, host, () => console.log(`序物设计商城：http://${host}:${port}（默认关闭收款）`));
for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => { app.close().then(() => process.exit(0)); });
