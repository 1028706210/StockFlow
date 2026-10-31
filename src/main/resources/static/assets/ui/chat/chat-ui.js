import { createConversation } from './chat-state.js';

function element(tag, className, text) {
  const el = document.createElement(tag);
  if (className) el.className = className;
  if (text !== undefined) el.textContent = text;
  return el;
}
function control(text, title, action, className = 'sf-chat-text-button') {
  const el = element('button', className, text);
  el.type = 'button'; el.title = title; el.setAttribute('aria-label', title);
  el.addEventListener('click', action);
  return el;
}

export function mountChat({ root, respond, identity = '业务规则 · 随时可问', onClose }) {
  root.classList.add('sf-chat');
  const head = element('header', 'sf-chat-head');
  const mark = element('span', 'sf-chat-mark', 'SF');
  mark.setAttribute('aria-hidden', 'true');
  const title = element('div', 'sf-chat-title');
  title.append(element('h2', '', 'StockFlow 咨询助手'));
  const identityText = element('p', 'sf-chat-identity', identity); title.append(identityText);
  const actions = element('div', 'sf-chat-head-actions');
  actions.append(control('清空', '清空当前对话', () => conversation.reset()));
  if (onClose) actions.append(control('×', '关闭咨询助手', onClose, 'sf-chat-close'));
  head.append(mark, title, actions);

  const log = element('div', 'sf-chat-log');
  log.setAttribute('role', 'log'); log.setAttribute('aria-label', '咨询消息');
  log.setAttribute('aria-live', 'polite'); log.setAttribute('aria-relevant', 'additions');
  const form = element('form', 'sf-chat-composer');
  const label = element('label', 'sf-chat-sr-only', '输入你的问题');
  const inputId = 'sf-chat-input-' + crypto.randomUUID();
  label.htmlFor = inputId;
  const input = element('textarea', 'sf-chat-input');
  input.id = inputId; input.rows = 2; input.maxLength = 2000;
  input.placeholder = '输入问题，例如：订单 SF-123 现在是什么状态？';
  input.spellcheck = false;
  const foot = element('div', 'sf-chat-composer-foot');
  const status = element('span', 'sf-chat-status', 'Enter 发送 · Shift+Enter 换行');
  status.setAttribute('role', 'status');
  const count = element('span', 'sf-chat-count', '0 / 1000');
  const send = element('button', 'sf-chat-send', '发送 ↑'); send.type = 'submit'; send.disabled = true;
  foot.append(status, count, send); form.append(label, input, foot);
  const note = element('p', 'sf-chat-note', '只读咨询 · 订单与库存操作请在工作台完成');
  root.replaceChildren(head, log, form, note);

  function updateComposer() {
    const length = [...input.value].length;
    count.textContent = `${length} / 1000`;
    count.classList.toggle('sf-chat-invalid', length > 1000);
    send.disabled = conversation.snapshot().pending || !input.value.trim() || length > 1000;
  }
  function sendText(text) {
    if (conversation.snapshot().pending || !text.trim() || [...text].length > 1000) return;
    input.value = ''; updateComposer();
    void conversation.send(text);
    input.focus();
  }
  function render(state) {
    log.replaceChildren();
    if (!state.messages.length) {
      const welcome = element('div', 'sf-chat-welcome');
      welcome.append(element('span', 'sf-chat-welcome-icon', '✦'), element('h3', '', '业务问题，清楚一点。'),
        element('p', '', '查商品、看订单，或先了解业务规则。'));
      const quick = element('div', 'sf-chat-quick');
      for (const text of ['商品列表', '我的订单', '如何取消订单', '订单多久超时']) {
        quick.append(control(text, text, () => sendText(text), 'sf-chat-quick-button'));
      }
      welcome.append(quick); log.append(welcome);
    }
    for (const message of state.messages) {
      const row = element('article', `sf-chat-message sf-chat-${message.role}`);
      row.append(element('span', 'sf-chat-message-label', message.role === 'user' ? '你' : message.role === 'error' ? '本次未完成' : 'StockFlow'));
      row.append(element('div', 'sf-chat-bubble', message.text));
      if (message.source) row.append(element('small', 'sf-chat-source', message.source === 'stockflow' ? '来自当前账号的实时查询' : message.source === 'knowledge' ? 'StockFlow 业务规则' : '能力范围提示'));
      if (message.retry) row.append(control('重新查询 ↗', '重试这条咨询', () => sendText(message.retry)));
      log.append(row);
    }
    if (state.pending) log.append(element('p', 'sf-chat-wait', '正在处理咨询…'));
    status.textContent = state.pending ? '正在处理，请稍候…' : 'Enter 发送 · Shift+Enter 换行';
    root.setAttribute('aria-busy', String(state.pending));
    updateComposer();
    requestAnimationFrame(() => { log.scrollTop = log.scrollHeight; });
  }
  const conversation = createConversation({ respond, changed: render });
  form.addEventListener('submit', event => { event.preventDefault(); sendText(input.value); });
  input.addEventListener('input', updateComposer);
  input.addEventListener('keydown', event => {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && event.keyCode !== 229) {
      event.preventDefault(); form.requestSubmit();
    }
  });
  render(conversation.snapshot());
  return {
    focus() { input.focus(); },
    reset() { input.value = ''; conversation.reset(); },
    setIdentity(text) { identityText.textContent = text; input.value = ''; conversation.reset(); },
  };
}
