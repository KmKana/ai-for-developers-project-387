import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

// Проверки публичной конфигурации единого шлюза: только файлы репозитория и
// git, без запуска самих проверок. Тест показывает, что локальная команда и CI
// действительно используют один и тот же набор gates.
// `test-check-gate` — самопроверка конфигурации шлюза из #22, а не новый
// продуктовый шов и не отдельная проверка приложения.

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const misePath = join(root, "mise.toml");
const workflowPath = join(root, ".github", "workflows", "check.yml");
const hexletPath = join(root, ".github", "workflows", "hexlet-check.yml");

const directGates = [
  "lint",
  "test",
  "test-frontend",
  "build-frontend",
  "check-contract",
  "test-e2e",
  "test-check-gate",
  "test-release-please",
];
const lintGates = ["lint-backend", "lint-frontend"];

function read(path) {
  return readFileSync(path, "utf8");
}

function parseTasks(text) {
  const tasks = {};
  let current = null;
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    const section = line.match(/^\[tasks\.([^\]]+)\]$/);
    if (section) {
      current = section[1];
      tasks[current] = {};
      continue;
    }
    if (line.startsWith("[")) {
      current = null;
      continue;
    }
    if (!current || line === "" || line.startsWith("#")) {
      continue;
    }
    const setting = line.match(/^([A-Za-z_-]+)\s*=\s*(.+)$/);
    if (setting) {
      tasks[current][setting[1]] = setting[2].trim();
    }
  }
  return tasks;
}

function listValue(value, label) {
  const match = value?.match(/^\[(.*)\]$/s);
  assert.ok(match, `${label}: ожидался список, получено ${value ?? "не задано"}`);
  return match[1]
    .split(",")
    .map((item) => item.trim().replace(/^["']|["']$/g, ""))
    .filter(Boolean);
}

function assertIncludesAll(actual, expected, label) {
  for (const item of expected) {
    assert.ok(actual.includes(item), `${label}: отсутствует ${item}; есть: ${actual.join(", ")}`);
  }
}

function assertNoHexletReferences(text, label) {
  assert.doesNotMatch(
    text,
    /hexlet-check\.yml|hexlet\/project-action|\.github\/workflows\/hexlet/i,
    `${label}: новый шлюз не должен ссылаться на workflow Hexlet`,
  );
}

test("mise check is the single gate with every required check", () => {
  const tasks = parseTasks(read(misePath));
  const check = tasks.check ?? {};
  assertIncludesAll(listValue(check.depends, "check.depends"), directGates, "check.depends");

  const lint = tasks.lint ?? {};
  assertIncludesAll(listValue(lint.depends, "lint.depends"), lintGates, "lint.depends");

  const commands = {
    "lint-backend": /spotless:check/,
    "lint-frontend": /npm run lint.*npm run format:check|npm run format:check.*npm run lint/,
    test: /mvn -B test/,
    "test-frontend": /npm run test/,
    "build-frontend": /npm run build/,
    "check-contract": /check-contract-artifacts\.mjs/,
    "test-e2e": /npm run test:e2e/,
    "test-check-gate": /check-gate\.test\.mjs/,
    "test-release-please": /release-please\.test\.mjs/,
  };
  for (const [name, pattern] of Object.entries(commands)) {
    assert.match(tasks[name]?.run ?? "", pattern, `задача ${name}: неверная команда`);
  }
  assertNoHexletReferences(read(misePath), "mise.toml");
});

test("browser E2E waits for every earlier gate and is locked to its ports", () => {
  const tasks = parseTasks(read(misePath));
  const e2e = tasks["test-e2e"] ?? {};
  const earlierGates = directGates.filter((gate) => gate !== "test-e2e");
  assertIncludesAll(
    listValue(e2e.wait_for, "test-e2e.wait_for"),
    earlierGates,
    "test-e2e.wait_for",
  );
  assert.ok(e2e.lock, "test-e2e.lock должен защищать фиксированные порты от двух прогонов");
});

test("separate GitHub Actions workflow runs mise run check on every push", () => {
  assert.ok(existsSync(workflowPath), "отсутствует .github/workflows/check.yml");
  const workflow = read(workflowPath);
  assert.ok(!workflow.includes("\t"), "YAML workflow не должен содержать табуляцию");
  assert.match(workflow, /^name:\s*check\s*$/m);
  assert.match(workflow, /^on:\s*$/m);
  assert.match(workflow, /^ {2}push:\s*$/m, "workflow должен запускаться на каждый push");
  assert.match(workflow, /^ {2}pull_request:\s*$/m);
  assert.match(workflow, /^ {2}workflow_dispatch:\s*$/m);
  assert.match(workflow, /uses:\s*jdx\/mise-action@v\d+\b/);
  assert.match(workflow, /^ {8}run:\s*mise run check\s*$/m);
  assertNoHexletReferences(workflow, "check.yml");
});

test("Hexlet workflow is present and untouched by this ticket", () => {
  assert.ok(existsSync(hexletPath), "отсутствует .github/workflows/hexlet-check.yml");
  const result = spawnSync(
    "git",
    ["diff", "--quiet", "HEAD", "--", ".github/workflows/hexlet-check.yml"],
    { cwd: root, encoding: "utf8" },
  );
  assert.equal(result.error, undefined, `git diff завершился ошибкой: ${result.error}`);
  assert.equal(
    result.status,
    0,
    `workflow Hexlet изменён: ${result.stdout}${result.stderr}`.trim(),
  );
});
