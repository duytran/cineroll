---
status: accepted
---

# ADR 0004: Opus 5.5 for brainstorming, Sonnet 5.5 for writing plans

> **Status:** Accepted · **Last updated:** 2026-11-24
> Brainstorming a design runs on Opus 5.5; writing the implementation plan from the approved spec runs on Sonnet 5.5.

## Context

Brainstorming is open-ended: it explores intent, weighs options and shapes a spec, so it benefits from the strongest reasoning. Writing a plan turns an already-approved spec into bite-sized tasks with exact files, code and commands, which is more mechanical and does not need the same depth.

## Decision

We use Opus 5.5 (`claude-opus-5-5`) while running the `brainstorming` skill, and Sonnet 5.5 (`claude-sonnet-5-5`) while running the `writing-plans` skill.

## Rationale

**Decided on:** spend the stronger model where design judgment is made, and the faster, cheaper one where the spec already fixes the answer.

- **Rejected: one model for everything** — either overpays for plan writing (Opus everywhere) or under-powers design exploration (Sonnet everywhere).
- **Rejected: Opus for plans too** — the spec is already approved, so plan quality depends mostly on following it exactly, not on deeper reasoning.

## Constraints & Rules

- **Always** use Opus 5.5 for the whole `brainstorming` session, up to the approved spec in `docs/specs/`.
- **Always** use Sonnet 5.5 for the whole `writing-plans` session, from the approved spec to the plan in `docs/plans/`.
- **Always** tell the user to switch the session model when the current one does not match the phase, before starting the skill. An agent cannot change the session's model without the user's approval.
- **Never** change the model mid-skill; switch at the hand-off between brainstorming and planning.

## Implementation

Switch with the model picker in the app, or `/model` in the CLI, before invoking the skill. Subagents spawned for a phase use the same model unless told otherwise.

## Consequences

- **Positive:** better design thinking where it matters; lower cost and latency for plan writing.
- **Negative / trade-off:** the user must switch models at the hand-off — **mitigated by** the agent reminding them before each phase.

## Compliance

At the start of a brainstorming or plan-writing session, the session's model matches the rule above, or the agent has asked the user to switch.

## Revisit when

A newer model family ships, or plans written by Sonnet need frequent rework from the spec.

## Related

- [`docs/specs/`](../specs/) — output of brainstorming.
- [`docs/plans/`](../plans/) — output of writing plans.
- [`CLAUDE.md`](../../CLAUDE.md) — points to this ADR.
