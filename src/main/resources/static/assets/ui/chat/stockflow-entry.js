import { requestJson } from '/api-client.mjs';
import { createReplyEngine } from './reply-core.js';
import { createSessionClient } from './session-client.js';
import { mountChat } from './chat-ui.js';

let session = { epoch: -1, me: null }, enginePromise;
const launcher = document.createElement('button');
launcher.type = 'button'; launcher.className = 'sf-chat-launcher';
launcher.textContent = '咨询助手'; launcher.hidden = true;
launcher.setAttribute('aria-haspopup', 'dialog'); launcher.setAttribute('aria-controls', 'sf-chat-dialog');
const dialog = document.createElement('dialog');
dialog.id = 'sf-chat-dialog'; dialog.className = 'sf-chat-dialog';
dialog.setAttribute('aria-label', 'StockFlow 咨询助手');
const root = document.createElement('section'); dialog.append(root);
document.body.append(launcher, dialog);

async function request(method, path) {
  const epoch = session.epoch;
  try { return await requestJson(() => session, method, path); }
  catch (error) {
    if (error.status === 401 && !error.stale && session.epoch === epoch) {
      window.dispatchEvent(new CustomEvent('stockflow:chat-expired', { detail: { epoch } }));
    }
    throw error;
  }
}
async function getEngine() {
  if (!enginePromise) {
    enginePromise = fetch(new URL('./knowledge.json', import.meta.url), { cache: 'no-store', credentials: 'same-origin' })
      .then(async response => {
        if (!response.ok) throw new Error('咨询规则尚未加载，请重新打开后重试。');
        return createReplyEngine({ client: createSessionClient(request), knowledge: await response.json() });
      }).catch(error => { enginePromise = undefined; throw error; });
  }
  return enginePromise;
}
const chat = mountChat({ root, onClose: () => dialog.close(), respond: async message => {
  const epoch = session.epoch;
  const engine = await getEngine();
  if (epoch !== session.epoch || !session.me) throw Object.assign(new Error('身份已切换'), { stale: true });
  return engine.reply(message);
} });
launcher.addEventListener('click', () => { if (session.me) { dialog.showModal(); chat.focus(); } });
dialog.addEventListener('close', () => { if (!launcher.hidden) launcher.focus(); });
dialog.addEventListener('click', event => {
  if (event.target !== dialog) return;
  const rect = dialog.getBoundingClientRect();
  if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close();
});
window.addEventListener('stockflow:session', event => {
  const next = event.detail;
  if (!next || !Number.isSafeInteger(next.epoch) || next.epoch === session.epoch) return;
  session = { epoch: next.epoch, me: next.me };
  launcher.hidden = !session.me;
  chat.setIdentity(session.me ? `${session.me.username} · 沿用当前工作台身份` : '请先登录工作台');
  if (!session.me && dialog.open) dialog.close();
});
window.dispatchEvent(new CustomEvent('stockflow:chat-ready'));
