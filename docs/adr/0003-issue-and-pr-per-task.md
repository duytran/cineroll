---
status: accepted
---

# ADR 0003: One GitHub issue and one PR per plan task

> **Status:** Accepted · **Last updated:** 2026-11-23
> Before a plan is implemented, each of its tasks gets a GitHub issue; each task is built on its own branch and merged through its own PR that closes that issue.

## Context

Plans in `docs/plans/` split work into tasks that each end in a testable deliverable and get their own review. Building a whole plan on one branch and opening one PR hides those per-task reviews, and makes the PR too large to review or merge task by task.

## Decision

We open one GitHub issue per plan task before writing code, build each task on its own branch, and merge it through one PR per task that closes its issue. A task that depends on the previous one is stacked: its branch starts from, and its PR targets, the previous task's branch.

## Rationale

**Decided on:** each task reviewable and mergeable on its own; a visible trail from plan task → issue → PR → commits; review findings land on the task they belong to.

- **Rejected: one branch and one PR per plan** — too large to review in one sitting, and no task can be merged, reverted or discussed on its own.
- **Rejected: PRs per task without issues** — the plan's task list never reaches GitHub, so there is no place to track what is open, blocked or done outside the PR list.
- **Rejected: one PR per task, all based on `main`** — later tasks need earlier tasks' code, so their PRs would not build until the earlier ones merge.

## Constraints & Rules

- **Always** ask the user once before creating anything on GitHub for a plan, showing the list of issues you will open (title per task). One approval covers that plan's issues, branches and PRs; pushes to `main` and merges still need their own approval.
- **Always** title issues `Task N: <task name>` (plan's wording); body = goal, files, test checklist, link to the plan section.
- **Always** write each issue number next to its task heading in the plan, e.g. `### Task 3: … (#12)`, in the same commit as the plan.
- **Always** name the branch `task/<issue>-<short-slug>`, e.g. `task/12-link-detection`.
- **Always** start task 1 from `main`; start a later task from the previous task's branch when it depends on it, otherwise from `main`. The PR's base is the branch you started from.
- **Always** put `Closes #<issue>` in the PR body, plus the task's test plan with what was actually run.
- **Always** commit review fixes for a task to that task's branch before its PR is merged — never into a later task.
- **Always** merge stacked PRs in task order; after merging one, retarget the next PR to `main` if GitHub did not.
- **Never** combine several plan tasks in one branch or PR.
- **Never** open a PR for a task whose tests, typecheck and CHANGELOG entry are not done.

## Implementation

Commands an agent uses:

```bash
gh issue create --title "Task 3: <task name>" --body-file /tmp/task3.md      # → #12
git switch -c task/12-<slug> task/11-<previous-slug>                         # stacked on task 2
git push -u origin task/12-<slug>
gh pr create --base task/11-<previous-slug> --head task/12-<slug> \
  --title "Task 3: <task name>" --body-file /tmp/pr12.md                     # body has "Closes #12"
```

## Consequences

- **Positive:** small PRs that match the plan's review gates; issue → PR → commit trail on GitHub; any task can be reverted on its own.
- **Negative / trade-off:** more GitHub objects to manage; stacked PRs need retargeting as they merge — **mitigated by** merging strictly in task order and the retarget rule above.
- **Negative / trade-off:** a fix to an early task after later tasks are stacked means rebasing the later branches — **unmitigated, accepted**; fix during that task's review, before later tasks start, where possible.

## Compliance

Review check before opening a PR: the branch name matches `task/<n>-…`, the body contains `Closes #<n>`, and the commits touch only that task's files. The plan's task headings show every task linked to an issue.

## Revisit when

The repo gets a second contributor with a different review flow, or tasks become so small that one PR per task costs more review time than it saves.

## Related

- [`docs/plans/`](../plans/) — where tasks are defined; issue numbers are written there.
- [`CLAUDE.md`](../../CLAUDE.md) — the change rules this workflow runs on.
