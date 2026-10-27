import { ReplyError } from './errors.js';

const STATUSES = new Set(['PENDING_CONFIRM', 'CONFIRMED', 'SHIPPED', 'COMPLETED', 'CANCELLED']);
export const invalidResponse = () => new ReplyError(502, 'UPSTREAM_INVALID_RESPONSE', 'StockFlow 返回了无法识别的数据，暂时无法查询。');
export const positiveId = value => Number.isSafeInteger(value) && value > 0;
const money = value => typeof value === 'number' && Number.isFinite(value) && value >= 0;
const safeText = (value, max) => typeof value === 'string' && value.trim().length > 0 &&
  [...value].length <= max && !/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/u.test(value);

function validLocalDateTime(value) {
  const parts = typeof value === 'string' && /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d{1,9})?$/.exec(value);
  if (!parts) return false;
  const [year, month, day, hour, minute, second] = parts.slice(1).map(Number);
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  return year > 0 && month >= 1 && month <= 12 && day >= 1 && day <= days[month - 1] &&
    hour <= 23 && minute <= 59 && second <= 59;
}

export function normalizeOrder(value, expectedId) {
  if (!value || !positiveId(value.id) || (expectedId !== undefined && value.id !== expectedId) ||
      !STATUSES.has(value.status) || !money(value.total) ||
      !validLocalDateTime(value.expiresAt) || ![null, 'USER', 'EXPIRED'].includes(value.cancelReason) ||
      (value.status !== 'CANCELLED' && value.cancelReason !== null)) throw invalidResponse();
  return { id: value.id, status: value.status, total: value.total, expiresAt: value.expiresAt, cancelReason: value.cancelReason };
}

export function normalizeProduct(value) {
  if (!value || !positiveId(value.id) || !safeText(value.name, 120) ||
      typeof value.code !== 'string' || !/^[A-Z0-9_-]{1,40}$/.test(value.code) || !money(value.price) || value.active !== true) throw invalidResponse();
  return { id: value.id, code: value.code, name: value.name, price: value.price, active: true };
}

export function normalizePage(value, normalize) {
  if (!value || !Array.isArray(value.items) || value.items.length > 5 ||
      !Number.isSafeInteger(value.total) || value.total < value.items.length || value.items.length !== Math.min(5, value.total) ||
      value.page !== 1 || value.size !== 5 || value.totalPages !== Math.max(1, Math.ceil(value.total / 5))) throw invalidResponse();
  return { items: value.items.map(item => normalize(item)), total: value.total };
}

