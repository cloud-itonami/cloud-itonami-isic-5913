# Contributing to cloud-itonami-isic-5913

Contributions should preserve the actor's scope: back-office distribution
coordination only, with CRITICAL exclusions of directly finalizing a
rights-licensing grant or a distribution-window/territory-clearance
decision (see README.md).

- All code must be .cljc (portable Clojure, no JVM-only constructs).
- Tests must pass: clojure -M:test
- Commit messages should link to relevant ADRs or issues.

**This actor does NOT:**
- Finalize a rights-licensing grant (who is licensed to distribute a
  title, in what territory, under what terms).
- Finalize a distribution-window or territory-clearance decision
  (locking in which window/territory a title actually releases in).
- Perform contract negotiation, minimum-guarantee/advance
  determination, or any legally binding distribution-agreement
  execution.

Contributions that cross these boundaries will be rejected.
