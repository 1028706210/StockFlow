export function createConversation({ respond, changed = () => {} }) {
  let messages = [], pending = false, generation = 0, sequence = 0;
  const snapshot = () => ({ pending, messages: messages.map(message => ({ ...message })) });
  const emit = () => changed(snapshot());
  const append = message => { messages.push({ id: ++sequence, ...message }); messages = messages.slice(-40); };
  return {
    snapshot,
    reset() { generation++; pending = false; messages = []; emit(); },
    async send(text) {
      if (pending || typeof text !== 'string' || !text.trim()) return false;
      text = text.trim();
      const current = generation;
      append({ role: 'user', text });
      pending = true;
      emit();
      try {
        const answer = await respond(text);
        if (current !== generation) return false;
        if (typeof answer?.reply !== 'string' || !answer.reply.trim()) throw new Error('回复格式不正确，请重试。');
        append({ role: 'assistant', text: answer.reply, source: answer.source });
      } catch (error) {
        if (current !== generation) return false;
        if (!error.stale) append({ role: 'error', text: error.message || '本次查询未完成，请重试。', retry: text });
      } finally {
        if (current === generation) { pending = false; emit(); }
      }
      return true;
    },
  };
}
