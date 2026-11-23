/**
 * Модель concurrency-группы workflow `opencode.yml`.
 *
 * В workflow используется выражение:
 *   opencode-${{ github.event.issue.number || github.event.pull_request.number }}-${{ github.event.comment.user.id }}
 *
 * Актор в группе нужен, чтобы новый комментарий бота (например, финальный
 * ответ OpenCode) не отменял пользовательский запуск. Два комментария одного
 * пользователя к одному PR по-прежнему конкурируют и отменяют устаревший.
 */
export const CONCURRENCY_PREFIX = "opencode";

export function concurrencyGroup({ number, actorId }) {
  const pr = Number(number);
  if (!Number.isInteger(pr) || pr <= 0) {
    throw new Error(`Некорректный номер Issue/PR: ${number}`);
  }
  if (actorId === undefined || actorId === null || String(actorId).length === 0) {
    throw new Error("Не указан actorId комментария");
  }
  return `${CONCURRENCY_PREFIX}-${pr}-${actorId}`;
}

/** GitHub отменяет предыдущий запуск, только если группа совпадает. */
export function wouldCancel(previousGroup, nextGroup) {
  return previousGroup === nextGroup;
}
