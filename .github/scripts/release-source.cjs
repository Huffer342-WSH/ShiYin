// Only a successful push-to-main run of this exact commit may supply a release.
function selectRun(runs, sha, repository) {
  return runs.filter(run => run.head_sha === sha && run.head_branch === 'main' &&
    run.event === 'push' && run.head_repository?.full_name?.toLowerCase() === repository.toLowerCase())
    .sort((a, b) => b.id - a.id)[0];
}
function usableArtifact(artifacts) {
  return artifacts.find(item => item.name === 'android-apks' && !item.expired && item.size_in_bytes > 0);
}
async function findSource({ github, context, core }) {
  const repository = context.repo.owner + '/' + context.repo.repo;
  const sha = process.env.RELEASE_COMMIT;
  for (let attempt = 0; attempt < 30; attempt++) {
    const { data } = await github.rest.actions.listWorkflowRuns({
      ...context.repo, workflow_id: 'android.yml', branch: 'main', event: 'push', head_sha: sha, per_page: 100
    });
    const run = selectRun(data.workflow_runs, sha, repository);
    if (!run) break;
    if (run.status !== 'completed') {
      core.info('Waiting for main CI run ' + run.id);
      await new Promise(resolve => setTimeout(resolve, 30000));
      continue;
    }
    if (run.conclusion !== 'success') throw new Error('Main CI did not pass: ' + run.html_url);
    const artifacts = await github.paginate(github.rest.actions.listWorkflowRunArtifacts,
      { ...context.repo, run_id: run.id, per_page: 100 });
    if (usableArtifact(artifacts)) {
      core.setOutput('run_id', String(run.id));
      await core.summary.addRaw('Reusing successful main CI: ' + run.html_url).write();
      return;
    }
    core.info('Successful CI artifact is missing or expired; run fresh checks.');
    break;
  }
  core.setOutput('run_id', '');
}
module.exports = { selectRun, usableArtifact, findSource };
