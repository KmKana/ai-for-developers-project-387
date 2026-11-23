import assert from "node:assert/strict";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

// Проверки публичной конфигурации релизов: файлы читаются как есть, без
// обращения к внутренним деталям release-please или к GitHub Actions.

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const configPath = join(root, ".github", "release-please-config.json");
const manifestPath = join(root, ".github", "release-please-manifest.json");
const workflowPath = join(root, ".github", "workflows", "release-please.yml");

function read(relativeOrAbsolutePath) {
  return readFileSync(relativeOrAbsolutePath, "utf8");
}

function readJson(path) {
  return JSON.parse(read(path));
}

function walkFiles(
  directory,
  ignored = new Set([".git", "node_modules", "dist", "target", "sdk"]),
) {
  const found = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (ignored.has(entry.name)) {
      continue;
    }
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      found.push(...walkFiles(path, ignored));
    } else if (entry.isFile()) {
      found.push(path);
    }
  }
  return found;
}

// Минимальная проверка синтаксиса YAML для простого workflow: без табуляции,
// без висячих пробелов и только ожидаемые формы строк (ключ, список, комментарий).
function assertSimpleYamlSyntax(text) {
  assert.ok(!text.includes("\t"), "YAML не должен содержать табуляцию");
  for (const [index, line] of text.split(/\r?\n/).entries()) {
    assert.ok(!/\s+$/.test(line), `строка ${index + 1}: висячий пробел`);
    if (line.trim() === "" || line.trimStart().startsWith("#")) {
      continue;
    }
    assert.match(
      line,
      /^ *(- )?(?:'[^']+'|"[^"]+"|[\w$.@/{}—-]+)(?::(?: .*)?| .*)?$/,
      `строка ${index + 1}: некорректная строка YAML: ${line}`,
    );
    const indent = line.length - line.trimStart().length;
    assert.equal(indent % 2, 0, `строка ${index + 1}: нечётный отступ`);
  }
}

test("release-please config is a parseable single-component simple config", () => {
  assert.ok(
    existsSync(configPath),
    "отсутствует .github/release-please-config.json",
  );
  const config = readJson(configPath);
  assert.equal(config["release-type"], "simple");
  assert.deepEqual(Object.keys(config.packages ?? {}), ["."]);
  assert.match(config.$schema ?? "", /release-please/);
  // Версию определяет release-please из conventional commits, а не ручные поля.
  for (const key of [
    "version-file",
    "release-as",
    "initial-version",
    "bootstrap-sha",
    "last-release-sha",
  ]) {
    assert.ok(
      !(key in config),
      `ручное управление версией через ${key} запрещено`,
    );
  }
});

test("release-please manifest is parseable and seeds the root component", () => {
  assert.ok(
    existsSync(manifestPath),
    "отсутствует .github/release-please-manifest.json",
  );
  const manifest = readJson(manifestPath);
  assert.deepEqual(Object.keys(manifest), ["."]);
  assert.match(manifest["."], /^\d+\.\d+\.\d+$/);
});

test("workflow is a distinct action workflow wired to the config", () => {
  assert.ok(
    existsSync(workflowPath),
    "отсутствует .github/workflows/release-please.yml",
  );
  const workflow = read(workflowPath);
  assertSimpleYamlSyntax(workflow);
  assert.match(workflow, /uses:\s*googleapis\/release-please-action@v4\b/);
  assert.match(
    workflow,
    /config-file:\s*\.github\/release-please-config\.json/,
  );
  assert.match(
    workflow,
    /manifest-file:\s*\.github\/release-please-manifest\.json/,
  );
  assert.match(workflow, /branches:\s*\n\s*-\s*main/);
  assert.match(workflow, /contents:\s*write/);
  assert.match(workflow, /pull-requests:\s*write/);
  assert.doesNotMatch(
    workflow,
    /hexlet\/project-action|npm version|changeset/i,
  );
});

test("no Changesets artifacts or dependencies are introduced", () => {
  assert.ok(
    !existsSync(join(root, ".changeset")),
    "каталог Changesets не должен добавляться",
  );
  for (const file of walkFiles(root)) {
    assert.ok(
      !/\.changeset|changesets?/i.test(file),
      `Changesets-артефакт: ${file}`,
    );
    if (file.endsWith("package.json") || file.endsWith("skills-lock.json")) {
      assert.doesNotMatch(
        read(file),
        /changesets?|@changesets\//i,
        `Changesets-зависимость в ${file}`,
      );
    }
  }
});

test("no manual version-bump files or scripts are added", () => {
  for (const entry of readdirSync(root)) {
    assert.doesNotMatch(
      entry,
      /^(version\.txt|VERSION|VERSION\.md|\.version)$/i,
      `файл ручной версии: ${entry}`,
    );
  }
  assert.ok(!existsSync(join(root, "version.txt")));
  for (const file of walkFiles(root)) {
    if (file.endsWith("package.json") || file.endsWith("mise.toml")) {
      assert.doesNotMatch(
        read(file),
        /["'](?:bump|version-bump|bump-version|release:bump)["']/i,
        `скрипт ручного bump: ${file}`,
      );
    }
  }
});
