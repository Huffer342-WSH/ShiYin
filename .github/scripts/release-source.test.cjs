const { test } = require('node:test');
const assert = require('node:assert/strict');
const { selectRun, usableArtifact, findSource } = require('./release-source.cjs');
const run = { id: 1, head_sha: 'abc', head_branch: 'main', event: 'push',
  head_repository: { full_name: 'owner/repo' } };
test('only exact commit pushed to main in this repo qualifies', () => {
  assert.equal(selectRun([run], 'abc', 'owner/repo'), run);
  for (const change of [{ head_sha: 'other' }, { head_branch: 'dev' }, { event: 'pull_request' },
    { head_repository: { full_name: 'fork/repo' } }]) {
    assert.equal(selectRun([{ ...run, ...change }], 'abc', 'owner/repo'), undefined);
  }
});
test('latest run wins so an old success cannot hide a new failure', () => {
  assert.equal(selectRun([run, { ...run, id: 2, conclusion: 'failure' }], 'abc', 'owner/repo').id, 2);
});
test('expired, empty and unrelated artifacts cannot be released', () => {
  assert.equal(usableArtifact([{ name: 'android-apks', expired: true, size_in_bytes: 5 }]), undefined);
  assert.equal(usableArtifact([{ name: 'android-apks', expired: false, size_in_bytes: 0 }]), undefined);
  assert.equal(usableArtifact([{ name: 'reports', expired: false, size_in_bytes: 5 }]), undefined);
  assert.ok(usableArtifact([{ name: 'android-apks', expired: false, size_in_bytes: 5 }]));
});

function fixture(runs, artifacts = []) {
  const outputs = {};
  return {
    outputs,
    github: { rest: { actions: {
      listWorkflowRuns: async () => ({ data: { workflow_runs: runs } }),
      listWorkflowRunArtifacts: {}
    } }, paginate: async () => artifacts },
    context: { repo: { owner: 'owner', repo: 'repo' } },
    core: { info() {}, setOutput: (key, value) => { outputs[key] = value; },
      summary: { addRaw() { return this; }, async write() {} } }
  };
}
test('no CI or expired artifacts request fresh verification', async () => {
  process.env.RELEASE_COMMIT = 'abc';
  for (const f of [fixture([]), fixture([{ ...run, status: 'completed', conclusion: 'success' }])]) {
    await findSource(f);
    assert.equal(f.outputs.run_id, '');
  }
});
test('failed CI blocks release instead of silently rebuilding', async () => {
  process.env.RELEASE_COMMIT = 'abc';
  await assert.rejects(findSource(fixture([{ ...run, status: 'completed', conclusion: 'failure' }])),
    /Main CI did not pass/);
});
test('successful exact CI with usable artifacts is reused', async () => {
  process.env.RELEASE_COMMIT = 'abc';
  const f = fixture([{ ...run, status: 'completed', conclusion: 'success' }],
    [{ name: 'android-apks', expired: false, size_in_bytes: 5 }]);
  await findSource(f);
  assert.equal(f.outputs.run_id, '1');
});
