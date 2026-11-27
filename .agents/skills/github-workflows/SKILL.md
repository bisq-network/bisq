---
name: github-workflows
description: "Create, change or review Bisq 1 GitHub Actions workflows in .github/workflows, including Dependabot action updates and failures of verifyGithubActionsSecurity. Use it for workflow triggers, token permissions, action pinning, expressions in run steps, Gradle and Java setup on runners, artifacts and the workflow guide."
---

# GitHub Workflows

[The workflow guide](../../../docs/github-actions-workflows.md) describes every workflow step by step and lists the [common rules](../../../docs/github-actions-workflows.md#common-rules). `verifyGithubActionsSecurity` enforces part of them, see [its checks](../../../docs/github-actions-workflows.md#verifygithubactionssecurity). For a security-relevant change, also use the `security-review` skill.

## Threat model

* GitHub expands `${{ }}` into the script text before the shell runs it. An expression with data an outside contributor controls, such as a pull request title, a branch name or a comment, is code injection. Pass every expression through `env` and quote the variable.
* `pull_request_target`, `workflow_run`, `issues` and `issue_comment` run in the base repository with its token and secrets, but outsiders can trigger them. Such a workflow must not check out, build or run code of the pull request. A `pull_request` run from a fork gets a read-only token and no secrets.
* An action reference that can move, a tag or a branch, runs whatever its owner publishes later with the token of the job. Pin the full commit SHA with the version tag as a comment.
* Data that crosses from an unprivileged run into a privileged one, through artifacts, caches, `GITHUB_ENV` or `GITHUB_OUTPUT`, is untrusted input there.

## Changing a workflow

* Set top-level permissions to `contents: read` or less, and grant more only in the job that needs it. `contribution-notice.yml` is the reviewed exception.
* A new privileged trigger, or a change to `contribution-notice.yml`, fails until `reviewedWorkflows` in `gradle/github-workflow-policy.gradle` holds the new digest. Update it only after the developer reviewed the jobs and permissions, and say so in the commit.
* Check the Gradle wrapper checksums before the first Gradle run on a runner, as Build Bisq does. `gradle/actions/setup-gradle` also needs `validate-wrappers: true`.
* Update the workflow guide in the same commit when the triggers, jobs, steps, permissions, artifacts or failure conditions change. Do not copy action versions or digests into it.
* Do not weaken `verifyGithubActionsSecurity` to make a workflow pass. Change the workflow, or raise the rule with the developer.

## Verify

Run `./gradlew verifyGithubActionsSecurity` until it passes; its YAML pass reports only after the line pass is clean. After a change to `gradle/github-workflow-policy.gradle`, also run `python3 gradle/tests/github_workflow_policy_test.py`. The task checks the rules above only in part, so also review the diff against the threat model. A workflow change is confirmed only by a run on GitHub; report which runs were observed and which were not.
