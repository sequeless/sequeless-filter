# 0000. Record architecture decisions

- Status: accepted
- Date: 2026-06-26

## Context

We need a lightweight, durable way to capture *why* non-obvious decisions are made, separate from the
code that captures *what* the system does. Decisions drift out of memory and chat history; new
contributors (human and agent) need the rationale without spelunking.

This ADR log starts empty. The **current design** is summarized in the
[README](../../README.md); ADRs from here on record the dated decisions that shape it as it evolves.

## Decision

We will use Architecture Decision Records (ADRs) in the [Nygard
format](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions). One ADR per
non-obvious decision, created from `_template.md` (run `make new-adr title="..."`), numbered
sequentially, and stored under `docs/adr/`. ADRs are immutable history: to change a decision we add a
new ADR that supersedes the old one rather than editing it. The README stays the living summary of the
current design; ADRs are the decision trail behind it.

## Consequences

- The rationale for decisions is discoverable and reviewable in PRs.
- A small amount of ceremony is added to decisions with alternatives; trivial choices need no ADR.
- `make new-adr title="..."` scaffolds the next record.
