import { normalizeOrder, normalizeProduct, normalizePage, positiveId } from './stockflow-data.js';
import { ReplyError } from './errors.js';

export function createSessionClient(request) {
  return {
    async searchProducts(query) {
      if (typeof query !== 'string' || [...query].length > 120) throw new ReplyError(400, 'INVALID_MESSAGE', '商品关键词不能超过 120 个字符。');
      const params = new URLSearchParams({ q: query, status: 'active', page: '1', size: '5' });
      return normalizePage((await request('GET', `/api/skus/page?${params}`)).data, normalizeProduct);
    },
    async getOrder(id) {
      if (!positiveId(id)) throw new ReplyError(400, 'INVALID_ORDER_ID', '订单编号必须是有效的正整数。');
      return normalizeOrder((await request('GET', `/api/orders/${id}`)).data, id);
    },
    async listOrders() {
      return normalizePage((await request('GET', '/api/orders/page?page=1&size=5')).data, normalizeOrder);
    },
  };
}
