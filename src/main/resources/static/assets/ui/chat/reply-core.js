import { ReplyError } from './errors.js';
import { normalizeBusinessText, normalizeProductQuery } from './chinese-input.js';

const statuses = {
  PENDING_CONFIRM: '待确认', CONFIRMED: '已确认', SHIPPED: '已发货', COMPLETED: '已完成', CANCELLED: '已取消',
};

function normalizeMessage(message) {
  if (typeof message !== 'string' || message.length > 2000 || [...message].length > 1000 ||
      /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f-\u009f\u202a-\u202e\u2066-\u2069]/u.test(message)) {
    throw new ReplyError(400, 'INVALID_MESSAGE', 'message 必须是 1–1000 个字符的文本，不能含控制字符。');
  }
  const text = message.normalize('NFKC').trim().replace(/\s+/gu, ' ');
  if (!text) throw new ReplyError(400, 'INVALID_MESSAGE', 'message 不能为空。');
  return text;
}

function orderLine(order) {
  let line = `SF-${order.id}：${statuses[order.status]}，金额 ¥${order.total.toFixed(2)}`;
  if (order.status === 'PENDING_CONFIRM') line += `，确认截止 ${order.expiresAt.replace('T', ' ')}（StockFlow 服务时间）`;
  if (order.cancelReason === 'EXPIRED') line += '，因超时取消';
  if (order.cancelReason === 'USER') line += '，买家主动取消';
  return line;
}

// Keep only fields needed to answer; buyer identifiers and backend internals stay private.
function orderData(order) {
  return { id: order.id, status: order.status, total: order.total, expiresAt: order.expiresAt, cancelReason: order.cancelReason };
}

export function createReplyEngine({ client, knowledge }) {
  const rules = knowledge.map(rule => ({ ...rule,
    keywords: [...new Set(rule.keywords.map(keyword => normalizeBusinessText(keyword).toLowerCase()))],
  }));
  return {
    async reply(message, { authorization } = {}) {
      const original = normalizeMessage(message);
      const text = normalizeBusinessText(original);
      const command = text.replace(/[?？!！。]+$/u, '').trim();
      const order = /^(?:(?:查询|查看|查)(?:一下)?\s*)?订单(?:\s*(?:编号|状态|进度))?\s*[:：#]?\s*(?:sf\s*-\s*)?(\d+)\s*(?:(?:的)?(?:状态|进度)|(?:现在)?是?什么状态)?$/iu.exec(command);
      if (order) {
        const id = Number(order[1]);
        if (!Number.isSafeInteger(id) || id <= 0) throw new ReplyError(400, 'INVALID_ORDER_ID', '订单编号必须是有效的正整数，例如：订单 SF-123。');
        const result = await client.getOrder(id, authorization);
        return { intent: 'order', reply: orderLine(result), source: 'stockflow', data: orderData(result) };
      }
      if (/^(?:(?:查询|查看)\s*)?我的订单$/u.test(command)) {
        const result = await client.listOrders(authorization);
        return {
          intent: 'orders', source: 'stockflow',
          reply: result.total ? `本人订单共 ${result.total} 笔，展示最近 ${result.items.length} 笔：\n${result.items.map(orderLine).join('\n')}` : '当前买家账号没有订单。',
          data: { items: result.items.map(orderData), total: result.total },
        };
      }
      const lower = text.toLowerCase();
      const rule = rules.find(item => item.keywords.some(keyword => lower.includes(keyword)));
      const product = /^(?:(?:查询|搜索|搜寻|查找|查看)\s*)?(?:商品|产品)(?:列表)?\s*[:：]?\s*(.*)$/u.exec(command);
      const explicitProduct = /^(?:查询|搜索|搜寻|查找|查看)/u.test(command) || /^(?:商品|产品)(?:列表|\s|[:：])/u.test(command);
      if (product && (explicitProduct || !rule)) {
        const originalCommand = original.replace(/[?？!！。]+$/u, '').trim();
        const query = originalCommand.slice(command.length - product[1].length).trim();
        let result = await client.searchProducts(query, authorization);
        const normalizedQuery = normalizeProductQuery(query);
        if (result.total === 0 && normalizedQuery !== query) {
          result = await client.searchProducts(normalizedQuery, authorization);
        }
        return {
          intent: 'products', source: 'stockflow',
          reply: result.total ? `在售商品共 ${result.total} 件，展示 ${result.items.length} 件：\n${result.items.map(item => `${item.name}（${item.code} / SKU ${item.id}）：¥${item.price.toFixed(2)}`).join('\n')}\n在售不保证库存充足，库存以下单时服务端校验为准。` : '没有找到符合关键词的在售商品，可换一个名称重新查询。',
          data: result,
        };
      }
      const numberedOrder = /订单\s*(?:编号|状态|进度)?\s*[:：#]?\s*(?:sf\s*-|[+-]?\d)/iu.test(command);
      if (numberedOrder && (!rule || rule.id === 'order-statuses')) {
        throw new ReplyError(400, 'INVALID_ORDER_ID', '订单编号格式不正确，请使用：订单 SF-123。');
      }
      if (rule) return { intent: rule.id, reply: rule.reply, source: 'knowledge' };
      return {
        intent: 'unknown', source: 'fallback',
        reply: '暂时无法回答这个问题。可输入“帮助”查看范围，或尝试“查询商品 鼠标”“订单 SF-123”“如何取消订单”。',
      };
    },
  };
}
