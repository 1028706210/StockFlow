export class ReplyError extends Error {
  constructor(status, code, message) {
    super(message);
    this.name = 'ReplyError';
    this.status = status;
    this.code = code;
  }
}

export function publicError(error) {
  return error instanceof ReplyError
    ? error
    : new ReplyError(500, 'INTERNAL_ERROR', '服务暂时无法处理，请稍后重试。');
}
