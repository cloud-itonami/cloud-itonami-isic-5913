# cloud-itonami-isic-5913

**Motion picture, video and television programme distribution activities** — ISIC Rev.4 class 5913.

A coordination-only actor for film/TV distribution back-office operations, behind an independent Governor that earns advisor trust through structured oversight: proposal → advise → govern → decide → commit|hold|escalate.

## Features

- **Closed proposal-op allowlist**: log-distribution-record, schedule-release-operation, flag-rights-concern, coordinate-delivery (all `:effect :propose`).
- **Two HARD governor checks** (permanent, un-overridable):
  1. **Title verified** — target title/contract record must exist AND be registered/verified in the store.
  2. **Effect is :propose** — any other `:effect` value is rejected.
  3. **Scope exclusion** — this actor NEVER finalizes a rights-licensing grant and NEVER finalizes a distribution-window/territory-clearance decision. Any proposal whose content attempts to finalize either is permanently blocked. An op outside the closed four-op allowlist is folded into the same check.
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: distribution-record logging only (approval-gated)
  - Phase 2: + release scheduling, delivery coordination (approval-gated)
  - Phase 3: auto-commits clean, high-confidence proposals (rights concerns always escalate)
- **Append-only audit ledger** — every decision is an immutable log entry.
- **langgraph-clj StateGraph** — one request = one supervised run; human-in-the-loop via `interrupt-before`.

## CRITICAL scope exclusions

This actor coordinates the *back office* around distribution decisions — it never makes the decisions themselves. It structurally cannot:

- **Finalize a rights-licensing grant** — who is licensed to distribute a title, in what territory, under what terms. That is always either a hard permanent block, or (for `:flag-rights-concern`) an always-escalate op requiring human sign-off.
- **Finalize a distribution-window or territory-clearance decision** — locking in which window/territory a title actually releases in. Same treatment.

The governor's `scope-excluded-terms` are deliberately phrased as the *finalization/execution action* ("finalize the rights license", "clear the distribution window"), never as a bare noun ("rights", "license", "window", "territory"), because this actor's own legitimate happy-path proposals — especially `:flag-rights-concern`, whose entire purpose is to talk *about* rights/clearance concerns — routinely use those bare nouns. `governor-test` and `governor-contract-test` both assert the default mock-advisor proposals never self-trip this check.

## Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:dev:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

## Test suite

- `test/filmdistops/governor_test.clj` — unit tests of governor hard checks, scope exclusion, and the self-trip regression test
- `test/filmdistops/advisor_test.clj` — advisor proposal shape and consistency
- `test/filmdistops/phase_test.clj` — rollout phase logic
- `test/filmdistops/governor_contract_test.clj` — full graph integration, audit trail
- `test/filmdistops/store_contract_test.clj` — Store protocol and MemStore implementation

## Modules

- `filmdistops.store` — SSoT (MemStore, String-keyed title directory, append-only ledger)
- `filmdistops.advisor` — contained intelligence node (mock + real-LLM seam)
- `filmdistops.governor` — independent compliance layer
- `filmdistops.phase` — staged rollout (0→3)
- `filmdistops.operation` — langgraph-clj StateGraph
- `filmdistops.sim` — demo driver

## License

AGPL-3.0-or-later. See LICENSE file.

## Governance

This actor is part of the cloud-itonami Wave 4 (human-services) fleet. See ADR-2607121000, ADR-2607152500, and the per-actor coverage ADR in `com-junkawasaki/root` `90-docs/adr/` for design decisions.
