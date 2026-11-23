import { createHash } from "node:crypto";

export const FINGERPRINT_PREFIX = "scheduled-audit-fingerprint";
export const DEMO_URL = "demo://scheduled-audit";
export const DEMO_CATEGORY = "demo";
export const DEMO_AUDIT_ID = "demo-largest-contentful-paint";
export const CATEGORY_ORDER = ["performance", "accessibility", "best-practices", "seo"];
/** Ниже этого score Lighthouse-аудит считается достаточно значимым для Issue. */
export const FINDING_SCORE_THRESHOLD = 0.9;

const ALLOWED_PROTOCOLS = new Set(["http:", "https:", "demo:"]);

/**
 * Читает JSON из PowerShell-файла без падений на UTF-8 BOM, UTF-16LE BOM
 * (Out-File по умолчанию) и UTF-16BE BOM. GitHub CLI в Actions пишет UTF-8,
 * но локальная проверка не должна зависеть от кодировки оболочки.
 */
export function parseJsonBuffer(buffer) {
  if (!Buffer.isBuffer(buffer)) {
    throw new Error("parseJsonBuffer ожидает Buffer");
  }
  let text;
  if (buffer.length >= 2 && buffer[0] === 0xff && buffer[1] === 0xfe) {
    text = buffer.subarray(2).toString("utf16le");
  } else if (buffer.length >= 2 && buffer[0] === 0xfe && buffer[1] === 0xff) {
    const swapped = Buffer.from(buffer.subarray(2));
    swapped.swap16();
    text = swapped.toString("utf16le");
  } else {
    text = buffer.toString("utf8").replace(/^\uFEFF/, "");
  }
  return JSON.parse(text);
}

/** Стабильный URL приложения без query/hash и завершающего слэша. */
export function normalizeAppUrl(input) {
  if (typeof input !== "string" || input.trim() === "") {
    throw new Error("APP_URL пуст");
  }
  const url = new URL(input.trim());
  if (!ALLOWED_PROTOCOLS.has(url.protocol)) {
    throw new Error(`Неподдерживаемый протокол APP_URL: ${url.protocol}`);
  }
  url.hash = "";
  url.search = "";
  url.pathname = url.pathname.replace(/\/+$/, "") || "/";
  return url.toString().replace(/\/$/, "");
}

/**
 * Fingerprint стабилен для приложения, типа проблемы и конкретного
 * правила/метрики Lighthouse. Значение метрики не входит: ухудшение той же
 * проблемы не должно создавать новый Issue.
 */
export function buildFingerprint({ appUrl, category, auditId }) {
  const payload = [normalizeAppUrl(appUrl), category, auditId].join("|");
  const digest = createHash("sha256").update(payload).digest("hex").slice(0, 32);
  return `sa-v1-${digest}`;
}

export function fingerprintMarker(fingerprint) {
  return `<!-- ${FINGERPRINT_PREFIX}: ${fingerprint} -->`;
}

export function findExistingIssue(issues, fingerprint) {
  const marker = fingerprintMarker(fingerprint);
  return (
    (issues ?? []).find(
      (issue) =>
        !issue.pull_request &&
        typeof issue.body === "string" &&
        issue.body.includes(marker),
    ) ?? null
  );
}

export function issueWebUrl(repo, number) {
  return `https://github.com/${repo}/issues/${number}`;
}

export function primaryFindingFor(summary) {
  if (!summary || !Array.isArray(summary.failingAudits)) return null;
  return summary.primaryFinding ?? null;
}

/**
 * Решение до/после вызова модели. Для demo всегда используется один и тот же
 * fingerprint, поэтому повторный запуск с тем же fixture не создаёт дубль.
 */
export function decideFinding({ summary, issues, repo, demo = false }) {
  if (!demo && !primaryFindingFor(summary)) {
    return { status: "NO_FINDING", fingerprint: null, existingIssueUrl: null };
  }

  const subject = demo
    ? { appUrl: DEMO_URL, category: DEMO_CATEGORY, auditId: DEMO_AUDIT_ID }
    : {
        appUrl: summary.url,
        category: summary.primaryFinding.category,
        auditId: summary.primaryFinding.id,
      };
  const fingerprint = buildFingerprint(subject);
  const existing = findExistingIssue(issues, fingerprint);

  if (existing) {
    return {
      status: "DUPLICATE_SKIPPED",
      fingerprint,
      existingIssueUrl: issueWebUrl(repo, existing.number),
    };
  }
  return { status: "NEW", fingerprint, existingIssueUrl: null };
}

function escapeTable(value) {
  return String(value ?? "").replace(/\|/g, "\\|").replace(/\r?\n/g, " ");
}

/** Выбирает одну конкретную проблему для Issue; метрики остаются в summary. */
export function selectPrimaryFinding(failingAudits) {
  const candidates = (failingAudits ?? []).filter(
    (audit) =>
      typeof audit.score === "number" &&
      audit.score < FINDING_SCORE_THRESHOLD &&
      !["informative", "notApplicable", "manual"].includes(audit.scoreDisplayMode),
  );

  candidates.sort((left, right) => {
    const byScore = left.score - right.score;
    if (byScore !== 0) return byScore;
    const leftCategory = CATEGORY_ORDER.indexOf(left.category);
    const rightCategory = CATEGORY_ORDER.indexOf(right.category);
    if (leftCategory !== rightCategory) return leftCategory - rightCategory;
    return String(left.id).localeCompare(String(right.id));
  });

  return candidates[0] ?? null;
}

/**
 * Превращает Lighthouse JSON в компактный summary для модели и fingerprint.
 * Никаких выдуманных показателей: если категории/аудиты отсутствуют, summary
 * будет без находки.
 */
export function summarizeLighthouseReport(report, { url, demo = false } = {}) {
  const categories = {};
  for (const [id, category] of Object.entries(report?.categories ?? {})) {
    categories[id] = {
      id,
      title: category.title ?? id,
      score: typeof category.score === "number" ? category.score : null,
    };
  }

  const failures = new Map();
  for (const [categoryId, category] of Object.entries(report?.categories ?? {})) {
    for (const ref of category.auditRefs ?? []) {
      const audit = report?.audits?.[ref.id];
      if (!audit || typeof audit.score !== "number" || audit.score >= 1) continue;
      if (["informative", "notApplicable", "manual"].includes(audit.scoreDisplayMode)) {
        continue;
      }
      const key = `${categoryId}:${ref.id}`;
      if (failures.has(key)) continue;
      failures.set(key, {
        category: categoryId,
        id: ref.id,
        title: audit.title ?? ref.id,
        score: audit.score,
        scoreDisplayMode: audit.scoreDisplayMode ?? "unknown",
        displayValue: audit.displayValue ?? "",
        description: String(audit.description ?? "").slice(0, 700),
        weight: ref.weight ?? 0,
      });
    }
  }

  const failingAudits = [...failures.values()];
  const primaryFinding = selectPrimaryFinding(failingAudits);
  const reportUrl = demo ? DEMO_URL : normalizeAppUrl(url ?? report?.finalUrl);

  return {
    mode: demo ? "demo" : "live",
    url: reportUrl,
    fetchTime: report?.fetchTime ?? null,
    lighthouseVersion: report?.lighthouseVersion ?? null,
    categories,
    failingAudits: failingAudits.slice(0, 25),
    primaryFinding,
    status: primaryFinding || demo ? "FINDING" : "NO_FINDING",
  };
}

/**
 * Формат ответа модели:
 *   STATUS: FINDING
 *   TITLE: ...
 *   BODY:
 *   ...
 *   END_BODY
 * либо STATUS: NO_FINDING.
 */
export function parseAgentOutput(text) {
  const source = String(text ?? "").trim();
  const status = source.match(/^STATUS:\s*(FINDING|NO_FINDING)\s*$/m)?.[1];
  if (!status) {
    throw new Error("В ответе модели нет строки STATUS: FINDING|NO_FINDING");
  }
  if (status === "NO_FINDING") {
    return { status, title: null, body: null };
  }

  const title = source.match(/^TITLE:\s*(.+)$/m)?.[1]?.trim();
  const bodyStart = source.indexOf("BODY:");
  const bodyEnd = source.lastIndexOf("END_BODY");
  if (!title || bodyStart < 0 || bodyEnd <= bodyStart) {
    throw new Error("В ответе модели нет TITLE/BODY/END_BODY");
  }
  const body = source.slice(bodyStart + "BODY:".length, bodyEnd).trim();
  if (!body) {
    throw new Error("Модель вернула пустой BODY");
  }
  return {
    status,
    title: title.replace(/\s+/g, " ").slice(0, 120),
    body,
  };
}

export function renderIssue({ agent, summary, context }) {
  if (!agent || agent.status !== "FINDING" || !agent.title || !agent.body) {
    throw new Error("renderIssue требует FINDING с title/body");
  }
  const demo = Boolean(context.demo);
  const title = `${demo ? "[DEMO] " : ""}${agent.title}`.slice(0, 120);
  const marker = fingerprintMarker(context.fingerprint);
  const lines = [
    "> *This issue was generated by AI during scheduled Lighthouse audit.*",
  ];
  if (demo) {
    lines.push(
      "> **DEMO / учебное:** это воспроизводимый фиктивный отчёт для проверки workflow. " +
        "Он не является реальным дефектом приложения.",
    );
  }
  lines.push(
    "",
    "## Контекст аудита",
    "",
    `- Проверенный URL: ${context.appUrl}`,
    `- Время отчёта: ${summary.fetchTime ?? "неизвестно"}`,
    `- Head SHA: \`${context.sha}\``,
    `- Workflow run: [${context.runUrl}](${context.runUrl})`,
    `- Артефакт: \`${context.artifactName}\``,
    demo ? "- Источник: фиктивный демонстрационный отчёт `scripts/scheduled-audit/fixtures/demo-lighthouse.json`" : "",
    "",
    "## Находка",
    "",
    agent.body,
    "",
    "## Lighthouse",
    "",
    "| Категория | Score |",
    "|---|---|",
    ...Object.values(summary.categories).map(
      (category) => `| ${escapeTable(category.title)} | ${category.score ?? "n/a"} |`,
    ),
    "",
  );

  const primary = summary.primaryFinding;
  if (primary) {
    lines.push(
      `Основное правило: ${primary.title} (\`${primary.id}\`, ${primary.category}), score ${primary.score}` +
        `${primary.displayValue ? `, ${primary.displayValue}` : ""}.`,
      "",
    );
  }
  lines.push(
    "## Fingerprint",
    "",
    `\`${context.fingerprint}\``,
    "",
    marker,
    "",
    "## Следующий шаг",
    "",
    "Проверить находку человеком, выбрать приоритет и при необходимости создать отдельный тикет на исправление.",
  );

  return {
    title,
    body: `${lines.join("\n").replace(/\n{3,}/g, "\n\n").trim()}\n`,
  };
}

export function isDemoRun({ eventName, demoInput }) {
  return eventName === "workflow_dispatch" && String(demoInput) === "true";
}
