#!/usr/bin/env node
/*
 * Budget guard for OpenCode GitHub Actions runs.
 *
 * The counter lives in GitHub Actions run history, not in an editable comment
 * and not in the workspace. Every workflow run that reaches the model is
 * counted, including reruns (`run_attempt`) and cancelled runs. Comments only
 * report the result of this guard.
 *
 * Interactive `/oc` calls: at most 3 model calls per Pull Request.
 * Automatic reviews: at most 2 model calls per Pull Request, and no second
 * review of the same head SHA.
 *
 * The script is fail-closed: if it cannot read the run history or jobs, the
 * workflow must not spend a model call.
 */

import { appendFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";

export const INTERACTIVE_WORKFLOW = "opencode.yml";
export const REVIEW_WORKFLOW = "opencode-review.yml";
export const INTERACTIVE_MAX = 3;
export const REVIEW_MAX = 2;
export const INTERACTIVE_PREFIX = "opencode";
export const REVIEW_PREFIX = "opencode review";
export const INTERACTIVE_MODEL_STEP = "Run OpenCode";
export const REVIEW_MODEL_STEP = "Run independent reviewer";

/** Run names are set by `run-name` in the workflows: `<prefix> / <pr> / <suffix>`. */
export function matchesPrRun(run, prefix, prNumber) {
  const title = run?.display_title ?? "";
  return title.startsWith(`${prefix} / ${prNumber} /`);
}

export function runAttempts(run) {
  const value = Number(run?.run_attempt);
  return Number.isInteger(value) && value > 0 ? value : 1;
}

/**
 * Returns the conclusion of the model step, `undefined` when the step or the
 * jobs payload is unavailable, and `"skipped"` when the model was not called.
 */
export function modelStepConclusion(jobs, stepName) {
  if (!Array.isArray(jobs) || jobs.length === 0) return undefined;
  const steps = jobs.flatMap((job) => (Array.isArray(job.steps) ? job.steps : []));
  const step = steps.find((item) => item.name === stepName);
  return step ? step.conclusion : undefined;
}

/**
 * Counts model attempts for one PR. A run counts when the model step ran, when
 * the run was cancelled, or when the run was retried. Runs that skipped the
 * model (budget limit, same SHA, integration guard) do not consume budget.
 *
 * The current run is counted explicitly even if the run-history API has not
 * returned it yet.
 */
export function countAttempts({
  runs,
  prefix,
  prNumber,
  currentRunId,
  jobsByRunId = {},
  modelStepName,
  trustedActor,
}) {
  let used = 0;
  let currentSeen = false;

  for (const run of runs ?? []) {
    if (!matchesPrRun(run, prefix, prNumber)) continue;
    if (trustedActor && run.triggering_actor?.login !== trustedActor) continue;
    if (run.conclusion === "skipped") continue;

    if (run.id === currentRunId) currentSeen = true;
    const attempts = runAttempts(run);

    if (run.id === currentRunId || run.conclusion === "cancelled" || attempts > 1) {
      used += attempts;
      continue;
    }

    const conclusion = modelStepConclusion(jobsByRunId[run.id], modelStepName);
    if (conclusion === "skipped") continue;
    used += attempts;
  }

  return { used, currentSeen };
}

/** True when a previous run of this workflow already reviewed the same head SHA. */
export function hasSuccessfulReview({
  runs,
  prNumber,
  headSha,
  currentRunId,
  jobsByRunId = {},
  modelStepName,
}) {
  return (runs ?? []).some((run) => {
    if (run.id === currentRunId) return false;
    if (!matchesPrRun(run, REVIEW_PREFIX, prNumber)) return false;
    if (run.head_sha !== headSha) return false;
    if (run.conclusion !== "success") return false;

    const conclusion = modelStepConclusion(jobsByRunId[run.id], modelStepName);
    if (conclusion === undefined) return true;
    return conclusion === "success";
  });
}

export function decideInteractive({ used, max, currentSeen }) {
  const effective = currentSeen ? used : used + 1;
  if (effective > max) return { allowed: false, reason: "limit_exhausted", used: effective };
  return { allowed: true, reason: "ok", used: effective };
}

export function decideReview({ used, max, currentSeen, alreadyReviewed }) {
  const effective = currentSeen ? used : used + 1;
  if (effective > max) return { allowed: false, reason: "limit_exhausted", used: effective };
  if (alreadyReviewed) return { allowed: false, reason: "same_sha_reviewed", used: effective };
  return { allowed: true, reason: "ok", used: effective };
}

// --- CLI -------------------------------------------------------------------

function apiBase() {
  return process.env.GITHUB_API_URL || "https://api.github.com";
}

function serverUrl() {
  return process.env.GITHUB_SERVER_URL || "https://github.com";
}

function runUrl(runId) {
  const repo = process.env.GITHUB_REPOSITORY || "";
  return `${serverUrl()}/${repo}/actions/runs/${runId}`;
}

function headers(token) {
  return {
    Authorization: `Bearer ${token}`,
    Accept: "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "opencode-budget-guard",
  };
}

function nextLink(link) {
  if (!link) return undefined;
  const part = link.split(",").find((item) => item.includes('rel="next"'));
  return part?.match(/<([^>]+)>/)?.[1];
}

async function getJson(url, token) {
  const response = await fetch(url, { headers: headers(token) });
  if (!response.ok) {
    throw new Error(`GitHub API ${response.status} for ${url}: ${await response.text()}`);
  }
  return { data: await response.json(), link: response.headers.get("link") };
}

async function paginate(url, token, key) {
  const items = [];
  let next = url;
  while (next) {
    const { data, link } = await getJson(next, token);
    items.push(...(key ? (data[key] ?? []) : data));
    next = nextLink(link);
  }
  return items;
}

function writeOutputs(values) {
  const body =
    Object.entries(values)
      .map(([key, value]) => `${key}=${String(value ?? "").replace(/\r?\n/g, " ")}`)
      .join("\n") + "\n";

  if (process.env.GITHUB_OUTPUT) appendFileSync(process.env.GITHUB_OUTPUT, body);
  else process.stdout.write(body);
}

function repoPath() {
  const repo = process.env.GITHUB_REPOSITORY;
  if (!repo || !repo.includes("/")) throw new Error("GITHUB_REPOSITORY is not set");
  return `/repos/${repo}`;
}

async function listRuns({ token, workflow, events }) {
  const runs = [];
  for (const event of events) {
    const path = `${repoPath()}/actions/workflows/${workflow}/runs?event=${event}&per_page=100`;
    runs.push(...(await paginate(`${apiBase()}${path}`, token, "workflow_runs")));
  }
  return runs;
}

async function jobsForPreviousRuns({ token, runs, currentRunId, predicate = () => true }) {
  const jobsByRunId = {};
  for (const run of runs) {
    if (run.id === currentRunId) continue;
    if (run.conclusion === "skipped") continue;
    if (!predicate(run)) continue;
    const path = `${repoPath()}/actions/runs/${run.id}/jobs?per_page=100`;
    jobsByRunId[run.id] = await paginate(`${apiBase()}${path}`, token, "jobs");
  }
  return jobsByRunId;
}

async function getPullRequest(token, prNumber) {
  return (await getJson(`${apiBase()}${repoPath()}/pulls/${prNumber}`, token)).data;
}

function limitFor(mode) {
  const configured = Number(process.env.MAX_CALLS);
  if (Number.isInteger(configured) && configured > 0) return configured;
  return mode === "review" ? REVIEW_MAX : INTERACTIVE_MAX;
}

async function main() {
  const mode = process.env.BUDGET_MODE;
  const token = process.env.GITHUB_TOKEN;
  const currentRunId = Number(process.env.GITHUB_RUN_ID);
  const prNumber = Number(process.env.PR_NUMBER);
  const limit = limitFor(mode);
  const logs = runUrl(process.env.GITHUB_RUN_ID || "");

  if (!token) throw new Error("GITHUB_TOKEN is not set");
  if (!["interactive", "review"].includes(mode)) throw new Error(`Unknown BUDGET_MODE: ${mode}`);
  if (!Number.isInteger(prNumber) || prNumber <= 0) {
    writeOutputs({
      allowed: "true",
      reason: "not_a_pull_request",
      used: "0",
      limit,
      pr: "",
      sha: "",
      logs,
    });
    return;
  }

  if (mode === "interactive") {
    const issue = (await getJson(`${apiBase()}${repoPath()}/issues/${prNumber}`, token)).data;
    if (!issue.pull_request) {
      writeOutputs({
        allowed: "true",
        reason: "not_a_pull_request",
        used: "0",
        limit,
        pr: prNumber,
        sha: "",
        logs,
      });
      return;
    }

    const pr = await getPullRequest(token, prNumber);
    const headSha = pr.head.sha;
    const runs = await listRuns({
      token,
      workflow: process.env.WORKFLOW_FILE || INTERACTIVE_WORKFLOW,
      events: ["issue_comment", "pull_request_review_comment"],
    });
    const trustedActor = process.env.TRUSTED_ACTOR || "KmKana";
    const matchingRuns = runs.filter(
      (run) =>
        matchesPrRun(run, INTERACTIVE_PREFIX, prNumber) &&
        run.triggering_actor?.login === trustedActor,
    );
    const jobsByRunId = await jobsForPreviousRuns({
      token,
      runs: matchingRuns,
      currentRunId,
    });
    const { used, currentSeen } = countAttempts({
      runs,
      prefix: INTERACTIVE_PREFIX,
      prNumber,
      currentRunId,
      jobsByRunId,
      modelStepName: INTERACTIVE_MODEL_STEP,
      trustedActor,
    });
    const decision = decideInteractive({ used, max: limit, currentSeen });

    writeOutputs({
      allowed: String(decision.allowed),
      reason: decision.reason,
      used: decision.used,
      limit,
      pr: prNumber,
      sha: headSha,
      logs,
    });
    return;
  }

  const headSha = process.env.HEAD_SHA || (await getPullRequest(token, prNumber)).head.sha;
  const runs = await listRuns({
    token,
    workflow: process.env.WORKFLOW_FILE || REVIEW_WORKFLOW,
    events: ["pull_request"],
  });
  const matchingRuns = runs.filter((run) => matchesPrRun(run, REVIEW_PREFIX, prNumber));
  const jobsByRunId = await jobsForPreviousRuns({
    token,
    runs: matchingRuns,
    currentRunId,
  });
  const { used, currentSeen } = countAttempts({
    runs,
    prefix: REVIEW_PREFIX,
    prNumber,
    currentRunId,
    jobsByRunId,
    modelStepName: REVIEW_MODEL_STEP,
  });
  const alreadyReviewed = hasSuccessfulReview({
    runs,
    prNumber,
    headSha,
    currentRunId,
    jobsByRunId,
    modelStepName: REVIEW_MODEL_STEP,
  });
  const decision = decideReview({ used, max: limit, currentSeen, alreadyReviewed });

  writeOutputs({
    allowed: String(decision.allowed),
    reason: decision.reason,
    used: decision.used,
    limit,
    pr: prNumber,
    sha: headSha,
    logs,
  });
}

const invokedDirectly =
  process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedDirectly) {
  main().catch((error) => {
    const mode = process.env.BUDGET_MODE;
    console.error(`opencode budget guard failed: ${error.message}`);
    writeOutputs({
      allowed: "false",
      reason: "infra_error",
      used: "0",
      limit: limitFor(mode),
      pr: process.env.PR_NUMBER || "",
      sha: process.env.HEAD_SHA || "",
      logs: runUrl(process.env.GITHUB_RUN_ID || ""),
      error: String(error.message).slice(0, 500),
    });
  });
}
