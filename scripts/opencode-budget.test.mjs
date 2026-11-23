import assert from "node:assert/strict";
import { test } from "node:test";

import {
  INTERACTIVE_MODEL_STEP,
  INTERACTIVE_PREFIX,
  REVIEW_MODEL_STEP,
  REVIEW_PREFIX,
  countAttempts,
  decideInteractive,
  decideReview,
  hasSuccessfulReview,
  matchesPrRun,
  modelStepConclusion,
  runAttempts,
} from "./opencode-budget.mjs";

// Проверки счётчика бюджета без GitHub API и без вызовов модели: логика
// решает, можно ли тратить платный вызов, поэтому она должна быть
// детерминированной и fail-closed.

const pr = 42;

function run(overrides = {}) {
  return {
    id: 1,
    display_title: `${INTERACTIVE_PREFIX} / ${pr} / KmKana`,
    conclusion: "success",
    run_attempt: 1,
    triggering_actor: { login: "KmKana" },
    ...overrides,
  };
}

function jobsWithStep(name, conclusion) {
  return [{ name: "job", steps: [{ name, conclusion }] }];
}

test("run name matches only the same PR and prefix", () => {
  assert.equal(matchesPrRun(run(), INTERACTIVE_PREFIX, pr), true);
  assert.equal(matchesPrRun(run(), INTERACTIVE_PREFIX, 43), false);
  assert.equal(matchesPrRun(run(), REVIEW_PREFIX, pr), false);
  assert.equal(
    matchesPrRun(
      run({ display_title: `${REVIEW_PREFIX} / ${pr} / abc` }),
      REVIEW_PREFIX,
      pr,
    ),
    true,
  );
  assert.equal(matchesPrRun(run({ display_title: "opencode review / 42" }), REVIEW_PREFIX, pr), false);
});

test("run attempts default to one and preserve reruns", () => {
  assert.equal(runAttempts({}), 1);
  assert.equal(runAttempts({ run_attempt: 0 }), 1);
  assert.equal(runAttempts({ run_attempt: 3 }), 3);
});

test("model step conclusion is skipped only when present", () => {
  assert.equal(modelStepConclusion(jobsWithStep("Run OpenCode", "success"), "Run OpenCode"), "success");
  assert.equal(modelStepConclusion(jobsWithStep("Run OpenCode", "skipped"), "Run OpenCode"), "skipped");
  assert.equal(modelStepConclusion([], "Run OpenCode"), undefined);
  assert.equal(modelStepConclusion([{ steps: [] }], "Run OpenCode"), undefined);
});

test("interactive budget counts the current, cancelled and rerun attempts", () => {
  const current = run({ id: 3, conclusion: null });
  const runs = [
    current,
    run({ id: 1 }),
    run({ id: 2, conclusion: "cancelled" }),
    run({ id: 4, conclusion: "skipped" }),
    run({ id: 5, display_title: `${INTERACTIVE_PREFIX} / 99 / KmKana` }),
    run({ id: 6, triggering_actor: { login: "someone" } }),
  ];
  const jobsByRunId = {
    1: jobsWithStep(INTERACTIVE_MODEL_STEP, "success"),
    2: [],
    4: [],
  };

  const { used, currentSeen } = countAttempts({
    runs,
    prefix: INTERACTIVE_PREFIX,
    prNumber: pr,
    currentRunId: 3,
    jobsByRunId,
    modelStepName: INTERACTIVE_MODEL_STEP,
    trustedActor: "KmKana",
  });

  assert.equal(currentSeen, true);
  assert.equal(used, 3);
  assert.equal(decideInteractive({ used, max: 3, currentSeen }).allowed, true);
});

test("interactive budget excludes runs whose model step was skipped", () => {
  const runs = [
    run({ id: 10, conclusion: "success" }),
    run({ id: 11, conclusion: "success" }),
  ];
  const jobsByRunId = {
    10: jobsWithStep(INTERACTIVE_MODEL_STEP, "skipped"),
    11: jobsWithStep(INTERACTIVE_MODEL_STEP, "success"),
  };

  const { used, currentSeen } = countAttempts({
    runs,
    prefix: INTERACTIVE_PREFIX,
    prNumber: pr,
    currentRunId: 999,
    jobsByRunId,
    modelStepName: INTERACTIVE_MODEL_STEP,
    trustedActor: "KmKana",
  });

  assert.equal(currentSeen, false);
  assert.equal(used, 1);
  assert.equal(decideInteractive({ used, max: 3, currentSeen }).used, 2);
});

test("interactive budget blocks the fourth call and counts reruns", () => {
  const runs = [
    run({ id: 1 }),
    run({ id: 2 }),
    run({ id: 3 }),
    run({ id: 4, conclusion: null }),
  ];
  const jobsByRunId = {
    1: jobsWithStep(INTERACTIVE_MODEL_STEP, "success"),
    2: jobsWithStep(INTERACTIVE_MODEL_STEP, "success"),
    3: jobsWithStep(INTERACTIVE_MODEL_STEP, "success"),
  };

  const { used, currentSeen } = countAttempts({
    runs,
    prefix: INTERACTIVE_PREFIX,
    prNumber: pr,
    currentRunId: 4,
    jobsByRunId,
    modelStepName: INTERACTIVE_MODEL_STEP,
    trustedActor: "KmKana",
  });
  const decision = decideInteractive({ used, max: 3, currentSeen });

  assert.equal(used, 4);
  assert.equal(decision.allowed, false);
  assert.equal(decision.reason, "limit_exhausted");

  const rerun = countAttempts({
    runs: [run({ id: 1, run_attempt: 2 }), run({ id: 2, conclusion: null })],
    prefix: INTERACTIVE_PREFIX,
    prNumber: pr,
    currentRunId: 2,
    jobsByRunId: {},
    modelStepName: INTERACTIVE_MODEL_STEP,
    trustedActor: "KmKana",
  });
  assert.equal(rerun.used, 3);
});

test("interactive budget fails closed when jobs are unavailable", () => {
  const { used } = countAttempts({
    runs: [run({ id: 1, conclusion: "failure" })],
    prefix: INTERACTIVE_PREFIX,
    prNumber: pr,
    currentRunId: 999,
    jobsByRunId: {},
    modelStepName: INTERACTIVE_MODEL_STEP,
    trustedActor: "KmKana",
  });
  assert.equal(used, 1);
});

test("successful review of the same SHA is detected and skipped", () => {
  const headSha = "abc123";
  const runs = [
    run({
      id: 1,
      display_title: `${REVIEW_PREFIX} / ${pr} / ${headSha}`,
      head_sha: headSha,
      conclusion: "success",
    }),
  ];
  const jobsByRunId = { 1: jobsWithStep(REVIEW_MODEL_STEP, "success") };

  const found = hasSuccessfulReview({
    runs,
    prNumber: pr,
    headSha,
    currentRunId: 2,
    jobsByRunId,
    modelStepName: REVIEW_MODEL_STEP,
  });
  assert.equal(found, true);

  const decision = decideReview({ used: 2, max: 2, currentSeen: true, alreadyReviewed: found });
  assert.equal(decision.allowed, false);
  assert.equal(decision.reason, "same_sha_reviewed");

  const skippedModel = hasSuccessfulReview({
    runs,
    prNumber: pr,
    headSha,
    currentRunId: 2,
    jobsByRunId: { 1: jobsWithStep(REVIEW_MODEL_STEP, "skipped") },
    modelStepName: REVIEW_MODEL_STEP,
  });
  assert.equal(skippedModel, false);
});

test("review budget allows two model attempts and blocks the third", () => {
  const runs = [
    run({ id: 1, display_title: `${REVIEW_PREFIX} / ${pr} / a`, conclusion: "success" }),
    run({ id: 2, display_title: `${REVIEW_PREFIX} / ${pr} / b`, conclusion: "failure" }),
    run({ id: 3, display_title: `${REVIEW_PREFIX} / ${pr} / c`, conclusion: null }),
  ];
  const jobsByRunId = {
    1: jobsWithStep(REVIEW_MODEL_STEP, "success"),
    2: jobsWithStep(REVIEW_MODEL_STEP, "failure"),
  };

  const { used, currentSeen } = countAttempts({
    runs,
    prefix: REVIEW_PREFIX,
    prNumber: pr,
    currentRunId: 3,
    jobsByRunId,
    modelStepName: REVIEW_MODEL_STEP,
  });
  const decision = decideReview({ used, max: 2, currentSeen, alreadyReviewed: false });

  assert.equal(used, 3);
  assert.equal(decision.allowed, false);
  assert.equal(decision.reason, "limit_exhausted");
});

test("review budget does not count a previous limit notice", () => {
  const runs = [
    run({ id: 1, display_title: `${REVIEW_PREFIX} / ${pr} / a`, conclusion: "success" }),
    run({ id: 2, display_title: `${REVIEW_PREFIX} / ${pr} / b`, conclusion: "success" }),
    run({ id: 3, display_title: `${REVIEW_PREFIX} / ${pr} / c`, conclusion: null }),
  ];
  const jobsByRunId = {
    1: jobsWithStep(REVIEW_MODEL_STEP, "success"),
    2: jobsWithStep(REVIEW_MODEL_STEP, "skipped"),
  };

  const { used, currentSeen } = countAttempts({
    runs,
    prefix: REVIEW_PREFIX,
    prNumber: pr,
    currentRunId: 3,
    jobsByRunId,
    modelStepName: REVIEW_MODEL_STEP,
  });

  assert.equal(used, 2);
  assert.equal(decideReview({ used, max: 2, currentSeen, alreadyReviewed: false }).allowed, true);
});
