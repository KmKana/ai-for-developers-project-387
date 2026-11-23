#!/usr/bin/env node
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";

import {
  DEMO_URL,
  decideFinding,
  parseAgentOutput,
  parseJsonBuffer,
  renderIssue,
  summarizeLighthouseReport,
} from "./lib.mjs";

const argv = process.argv.slice(2);
const command = argv[0];

function value(name) {
  const index = argv.indexOf(`--${name}`);
  return index >= 0 ? argv[index + 1] : undefined;
}

function flag(name) {
  return argv.includes(`--${name}`);
}

function required(name) {
  const result = value(name);
  if (!result) throw new Error(`Не передан --${name}`);
  return result;
}

function readJson(path) {
  return parseJsonBuffer(readFileSync(path));
}

function writeJson(path, valueToWrite) {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, `${JSON.stringify(valueToWrite, null, 2)}\n`, "utf8");
}

function writeText(path, text) {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, text, "utf8");
}

function summarize() {
  const report = readJson(required("report"));
  const demo = flag("demo");
  const summary = summarizeLighthouseReport(report, {
    url: value("url"),
    demo,
  });
  writeJson(required("output"), summary);
  console.log(`summarize: ${summary.status}, mode=${summary.mode}`);
}

function decide() {
  const summary = readJson(required("summary"));
  const issues = readJson(required("issues"));
  const repo = value("repo") ?? process.env.GITHUB_REPOSITORY;
  if (!repo) throw new Error("Не передан --repo и нет GITHUB_REPOSITORY");
  const decision = decideFinding({
    summary,
    issues,
    repo,
    demo: flag("demo"),
  });
  writeJson(required("output"), decision);
  console.log(`decide: ${decision.status}, fingerprint=${decision.fingerprint ?? "none"}`);
}

function issue() {
  const decision = readJson(required("decision"));
  const outputDir = required("output-dir");
  mkdirSync(outputDir, { recursive: true });

  if (decision.status !== "NEW") {
    writeJson(`${outputDir}/issue-result.json`, {
      status: decision.status,
      issueUrl: decision.existingIssueUrl ?? null,
    });
    console.log(`issue: skipped (${decision.status})`);
    return;
  }

  const summary = readJson(required("summary"));
  const agent = parseAgentOutput(readFileSync(required("agent-output"), "utf8"));
  if (agent.status === "NO_FINDING") {
    writeJson(`${outputDir}/issue-result.json`, { status: "NO_FINDING", issueUrl: null });
    console.log("issue: NO_FINDING");
    return;
  }

  const demo = flag("demo");
  const appUrl = demo ? DEMO_URL : summary.url;
  const rendered = renderIssue({
    agent,
    summary,
    context: {
      demo,
      appUrl,
      fingerprint: decision.fingerprint,
      runUrl: required("run-url"),
      sha: required("sha"),
      artifactName: required("artifact"),
    },
  });

  writeText(`${outputDir}/issue-title.txt`, rendered.title);
  writeText(`${outputDir}/issue-body.md`, rendered.body);
  writeJson(`${outputDir}/issue-result.json`, {
    status: "FINDING",
    title: rendered.title,
    body: rendered.body,
    fingerprint: decision.fingerprint,
  });
  console.log(`issue: FINDING, title=${rendered.title}`);
}

try {
  if (command === "summarize") summarize();
  else if (command === "decide") decide();
  else if (command === "issue") issue();
  else {
    throw new Error("Использование: cli.mjs summarize|decide|issue ...");
  }
} catch (error) {
  console.error(`scheduled-audit: ${error.message}`);
  process.exit(1);
}
