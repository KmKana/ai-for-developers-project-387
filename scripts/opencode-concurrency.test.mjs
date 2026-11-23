import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

import { concurrencyGroup, wouldCancel } from "./opencode-concurrency.mjs";

// Проверяем и модель групп, и фактическую конфигурацию workflow: тест должен
// падать, если из group уберут actor или снова сделают общую группу для PR.

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const workflow = readFileSync(join(root, ".github", "workflows", "opencode.yml"), "utf8");

test("workflow scopes concurrency by Issue/PR and commenter id", () => {
  const groupLine = workflow
    .split(/\r?\n/)
    .find((line) => line.trimStart().startsWith("group: opencode-"));

  assert.ok(groupLine, "в opencode.yml не найдена concurrency group");
  assert.match(
    groupLine,
    /github\.event\.issue\.number \|\| github\.event\.pull_request\.number/,
    "группа должна учитывать номер Issue/PR",
  );
  assert.match(
    groupLine,
    /github\.event\.comment\.user\.id/,
    "группа должна учитывать автора комментария",
  );
  assert.match(workflow, /^ {2}cancel-in-progress: true$/m);
});

test("budget limit and model gating are preserved", () => {
  assert.match(workflow, /MAX_CALLS: "3"/);
  const gated = workflow.match(/needs\.budget\.outputs\.allowed == 'true'/g) ?? [];
  assert.ok(gated.length >= 2, "readonly и write должны запускаться только при allowed");
});

test("bot and user comments use different groups and do not cancel each other", () => {
  const user = concurrencyGroup({ number: 30, actorId: 1001 });
  const bot = concurrencyGroup({ number: 30, actorId: 2002 });

  assert.notEqual(user, bot);
  assert.equal(wouldCancel(user, bot), false);
  assert.equal(wouldCancel(bot, user), false);
});

test("two comments from the same user on one PR still cancel the outdated run", () => {
  const first = concurrencyGroup({ number: 30, actorId: 1001 });
  const second = concurrencyGroup({ number: 30, actorId: 1001 });

  assert.equal(wouldCancel(first, second), true);
});

test("the same actor on different Issues/PRs does not cancel another run", () => {
  const pr30 = concurrencyGroup({ number: 30, actorId: 1001 });
  const pr31 = concurrencyGroup({ number: 31, actorId: 1001 });

  assert.equal(wouldCancel(pr30, pr31), false);
});
