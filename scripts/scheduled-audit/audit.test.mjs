import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

import {
  DEMO_URL,
  buildFingerprint,
  decideFinding,
  findExistingIssue,
  fingerprintMarker,
  isDemoRun,
  normalizeAppUrl,
  parseAgentOutput,
  parseJsonBuffer,
  renderIssue,
  selectPrimaryFinding,
  summarizeLighthouseReport,
} from "./lib.mjs";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const demoReport = JSON.parse(
  readFileSync(join(root, "scripts/scheduled-audit/fixtures/demo-lighthouse.json"), "utf8"),
);
const demoSummary = summarizeLighthouseReport(demoReport, { url: DEMO_URL, demo: true });

test("normalizes URL for a stable fingerprint", () => {
  assert.equal(normalizeAppUrl("https://Example.COM/path/?a=1#frag"), "https://example.com/path");
  assert.equal(normalizeAppUrl("https://example.com/"), "https://example.com");
  assert.equal(normalizeAppUrl(DEMO_URL), DEMO_URL);
  assert.throws(() => normalizeAppUrl(""));
});

test("fingerprint depends on app, category and audit rule, not metric value", () => {
  const first = buildFingerprint({
    appUrl: "https://app.example/",
    category: "performance",
    auditId: "largest-contentful-paint",
  });
  const same = buildFingerprint({
    appUrl: "https://app.example",
    category: "performance",
    auditId: "largest-contentful-paint",
  });
  const otherAudit = buildFingerprint({
    appUrl: "https://app.example",
    category: "performance",
    auditId: "total-blocking-time",
  });
  const otherApp = buildFingerprint({
    appUrl: "https://other.example",
    category: "performance",
    auditId: "largest-contentful-paint",
  });

  assert.match(first, /^sa-v1-[a-f0-9]{32}$/);
  assert.equal(first, same);
  assert.notEqual(first, otherAudit);
  assert.notEqual(first, otherApp);
});

test("finds duplicate in open/closed issues and ignores pull requests", () => {
  const fingerprint = "sa-v1-abc";
  const marker = fingerprintMarker(fingerprint);
  const issues = [
    { number: 1, body: `text\n${marker}\n`, html_url: "https://github.com/o/r/issues/1" },
    { number: 2, body: `PR ${marker}`, pull_request: {} },
  ];

  assert.equal(findExistingIssue(issues, "sa-v1-missing"), null);
  assert.equal(findExistingIssue(issues, fingerprint)?.number, 1);
});

test("Lighthouse fixture produces a deterministic demo finding", () => {
  assert.equal(demoSummary.mode, "demo");
  assert.equal(demoSummary.status, "FINDING");
  assert.equal(demoSummary.primaryFinding.id, "largest-contentful-paint");
  assert.equal(demoSummary.categories.performance.score, 0.42);
  assert.equal(demoSummary.failingAudits.length, 2);
});

test("a clean Lighthouse report has no finding", () => {
  const report = {
    categories: {
      performance: {
        id: "performance",
        title: "Performance",
        score: 1,
        auditRefs: [{ id: "largest-contentful-paint", weight: 25 }],
      },
    },
    audits: {
      "largest-contentful-paint": {
        id: "largest-contentful-paint",
        title: "LCP",
        score: 1,
        scoreDisplayMode: "numeric",
      },
    },
  };
  const summary = summarizeLighthouseReport(report, { url: "https://app.example" });
  assert.equal(summary.status, "NO_FINDING");
  assert.equal(summary.primaryFinding, null);
});

test("primary finding is the lowest failing score with stable ordering", () => {
  const primary = selectPrimaryFinding([
    { id: "b", category: "performance", score: 0.4, scoreDisplayMode: "numeric" },
    { id: "a", category: "accessibility", score: 0.4, scoreDisplayMode: "numeric" },
    { id: "c", category: "performance", score: 0, scoreDisplayMode: "numeric" },
    { id: "ignored", category: "performance", score: null, scoreDisplayMode: "numeric" },
  ]);
  assert.equal(primary.id, "c");
});

test("minor audits at or above the 0.9 threshold do not create a finding", () => {
  assert.equal(
    selectPrimaryFinding([
      { id: "minor", category: "performance", score: 0.95, scoreDisplayMode: "numeric" },
    ]),
    null,
  );
});

test("new finding is decided as NEW, then duplicate is skipped with existing URL", () => {
  const first = decideFinding({
    summary: demoSummary,
    issues: [],
    repo: "o/r",
    demo: true,
  });
  assert.equal(first.status, "NEW");

  const rendered = renderIssue({
    agent: {
      status: "FINDING",
      title: "Slow Largest Contentful Paint",
      body: "Проблема: LCP медленный.\n\nИсправление: оптимизировать изображения.",
    },
    summary: demoSummary,
    context: {
      demo: true,
      appUrl: DEMO_URL,
      fingerprint: first.fingerprint,
      runUrl: "https://github.com/o/r/actions/runs/1",
      sha: "abc123",
      artifactName: "scheduled-audit-1",
    },
  });

  const second = decideFinding({
    summary: demoSummary,
    issues: [{ number: 42, body: rendered.body }],
    repo: "o/r",
    demo: true,
  });

  assert.equal(second.status, "DUPLICATE_SKIPPED");
  assert.equal(second.fingerprint, first.fingerprint);
  assert.equal(second.existingIssueUrl, "https://github.com/o/r/issues/42");
});

test("a summary without a finding is NO_FINDING even when issues exist", () => {
  const decision = decideFinding({
    summary: { url: "https://app.example", primaryFinding: null, failingAudits: [] },
    issues: [],
    repo: "o/r",
    demo: false,
  });
  assert.equal(decision.status, "NO_FINDING");
  assert.equal(decision.fingerprint, null);
});

test("agent output parser handles FINDING, NO_FINDING and malformed output", () => {
  const finding = parseAgentOutput(
    "STATUS: FINDING\nTITLE: Slow LCP\nBODY:\nПроблема и исправление.\nEND_BODY\n",
  );
  assert.deepEqual(finding, {
    status: "FINDING",
    title: "Slow LCP",
    body: "Проблема и исправление.",
  });
  assert.equal(parseAgentOutput("STATUS: NO_FINDING").status, "NO_FINDING");
  assert.throws(() => parseAgentOutput("no status"));
  assert.throws(() => parseAgentOutput("STATUS: FINDING\nBODY:\nx\nEND_BODY"));
});

test("renderIssue includes demo warning, context, fingerprint and report link", () => {
  const rendered = renderIssue({
    agent: {
      status: "FINDING",
      title: "Slow Largest Contentful Paint",
      body: "Проблема: LCP медленный.\n\nПоследствия: страница кажется зависшей.\n\nИсправление: сжать изображение.",
    },
    summary: demoSummary,
    context: {
      demo: true,
      appUrl: DEMO_URL,
      fingerprint: "sa-v1-demo",
      runUrl: "https://github.com/o/r/actions/runs/1",
      sha: "abc123",
      artifactName: "scheduled-audit-1",
    },
  });

  assert.match(rendered.title, /^\[DEMO\] /);
  assert.match(rendered.body, /DEMO \/ учебное/);
  assert.match(rendered.body, /Largest Contentful Paint/);
  assert.match(rendered.body, /scheduled-audit-1/);
  assert.match(rendered.body, new RegExp(fingerprintMarker("sa-v1-demo")));
  assert.match(rendered.body, /https:\/\/github.com\/o\/r\/actions\/runs\/1/);
});

test("demo mode only comes from an explicit workflow_dispatch input", () => {
  assert.equal(isDemoRun({ eventName: "workflow_dispatch", demoInput: "true" }), true);
  assert.equal(isDemoRun({ eventName: "workflow_dispatch", demoInput: "false" }), false);
  assert.equal(isDemoRun({ eventName: "schedule", demoInput: undefined }), false);
});

test("JSON reader accepts UTF-8 BOM and UTF-16LE/BE BOM from PowerShell", () => {
  const payload = { hello: "мир" };
  const json = JSON.stringify(payload);
  const utf8Bom = Buffer.concat([Buffer.from([0xef, 0xbb, 0xbf]), Buffer.from(json, "utf8")]);
  const utf16le = Buffer.concat([Buffer.from([0xff, 0xfe]), Buffer.from(json, "utf16le")]);
  const utf16beBody = Buffer.from(json, "utf16le");
  utf16beBody.swap16();
  const utf16be = Buffer.concat([Buffer.from([0xfe, 0xff]), utf16beBody]);

  assert.deepEqual(parseJsonBuffer(utf8Bom), payload);
  assert.deepEqual(parseJsonBuffer(utf16le), payload);
  assert.deepEqual(parseJsonBuffer(utf16be), payload);
});

test("audit model config is a separate read-only agent", () => {
  const config = JSON.parse(readFileSync(join(root, "opencode.audit.json"), "utf8"));
  assert.equal(config.default_agent, "auditor");
  assert.equal(config.permission.edit, "deny");
  assert.equal(config.permission.bash, "deny");
  assert.equal(config.permission.task, "deny");
  assert.equal(config.permission.webfetch, "deny");
  assert.equal(config.permission.external_directory["*"], "deny");
  assert.equal(config.permission.read["*.env"], "deny");
  assert.equal(config.agent.auditor.mode, "primary");
  assert.equal(config.agent.auditor.permission.edit, "deny");
});

test("workflow is scheduled daily in Asia/Almaty, manual demo and least privilege", () => {
  const workflow = readFileSync(
    join(root, ".github/workflows/scheduled-audit.yml"),
    "utf8",
  );
  assert.match(workflow, /^name:\s*scheduled-audit$/m);
  assert.match(workflow, /cron:\s*"17 9 \* \* \*"/);
  assert.equal((workflow.match(/cron:\s*"/g) ?? []).length, 1, "должно быть одно расписание");
  assert.match(workflow, /timezone:\s*"Asia\/Almaty"/);
  assert.match(workflow, /workflow_dispatch:/);
  assert.match(workflow, /demo_finding:/);
  assert.match(workflow, /type:\s*boolean/);
  assert.match(workflow, /default:\s*false/);
  assert.match(workflow, /contents:\s*read/);
  assert.match(workflow, /issues:\s*write/);
  assert.doesNotMatch(workflow, /pull-requests:/);
  assert.match(workflow, /timeout-minutes:\s*20/);
  assert.match(workflow, /group:\s*scheduled-audit/);
  assert.match(workflow, /cancel-in-progress:\s*false/);
  assert.match(workflow, /ref:\s*main/);
  assert.match(workflow, /vars\.APP_URL/);
  assert.match(workflow, /uses:\s*actions\/upload-artifact@v4/);
  assert.match(workflow, /scripts\/scheduled-audit\/fixtures\/demo-lighthouse\.json/);
  assert.match(workflow, /if:\s*steps\.decide\.outputs\.status == 'NEW'/);
  assert.match(workflow, /gh issue create/);
  assert.doesNotMatch(workflow, /gh pr create/);
  assert.doesNotMatch(workflow, /^ {2}push:/m);
  assert.doesNotMatch(workflow, /^ {2}pull_request:/m);
  assert.doesNotMatch(workflow, /callbooking-uklz\.onrender\.com/);
  assert.doesNotMatch(workflow, /git (commit|push)/);
});

test("NO_DATA is handled before model and demo branch does not require APP_URL", () => {
  const workflow = readFileSync(
    join(root, ".github/workflows/scheduled-audit.yml"),
    "utf8",
  );
  const demoIndex = workflow.indexOf('echo "status=DEMO"');
  const appUrlIndex = workflow.indexOf('if [ -z "$APP_URL" ]');
  assert.ok(demoIndex >= 0 && appUrlIndex > demoIndex, "demo должен обходиться без APP_URL");
  assert.match(workflow, /if \[ "\$status" != "READY" \]; then[\s\S]*?exit 1/);
  assert.doesNotMatch(workflow, /GH_TOKEN[\s\S]{0,200}opencode run/);
});

test("reports directory is ignored by git", () => {
  const gitignore = readFileSync(join(root, ".gitignore"), "utf8");
  assert.match(gitignore, /^scheduled-audit-output\/$/m);
});
