---
description: Run an independent AI code review of the current branch (vs main) before opening a PR.
---

Run a self-review of the current branch against `main` using the `code-reviewer` subagent.

## Preflight

1. Confirm we are **not** on `main`. If we are, stop and tell the user to switch to a feature branch.
2. Confirm the working tree is clean (`git status --short` is empty). If there are uncommitted changes, ask the user whether to (a) stash them, (b) commit them first, or (c) proceed and accept that they will not be reviewed.
3. Confirm the branch has commits ahead of `main` (`git log main..HEAD --oneline` non-empty). If empty, stop — nothing to review.

## Spawn the reviewer

Spawn the `code-reviewer` subagent via the `Agent` tool with these exact parameters:

- `subagent_type`: `code-reviewer`
- `description`: `Self-review of <branch name>`
- `prompt`: a self-contained briefing that includes:
  - "Review the changes on the current branch (`<branch name>`) compared to `main`."
  - "Follow the workflow in your agent description: load project conventions, inspect the diff, read changed files in full, apply the focus areas, output in the punch list format."
  - "Do not modify any code. Report findings only."

Do not pre-summarise the change to the reviewer. The point of an independent reviewer is that it sees the change without the writer's framing.

## Relay

When the agent returns:

1. **Print its report verbatim** to the user — do not summarise, do not paraphrase, do not add commentary.
2. After the report, ask the user how they want to proceed. Present these options:
   - Fix all BLOCKER + MAJOR now (you address them in sequence)
   - Fix only BLOCKER now, log MAJOR as follow-up tasks
   - Open the PR as-is (override)
   - Discuss specific findings before deciding

3. Whatever the user picks, **do not auto-merge any PR** — that is a human step.
