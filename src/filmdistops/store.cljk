(ns filmdistops.store
  "SSoT for the ISIC-5913 motion-picture/video/TV-programme DISTRIBUTION
  COORDINATION actor, behind a `Store` protocol so the backend is a
  swap, not a rewrite -- the same seam every `cloud-itonami-isic-*`
  actor in this fleet uses.

  This actor coordinates the back-office operations of a film/TV
  distribution desk: title/territory/window record logging,
  release-date and marketing-window scheduling proposals, rights- and
  clearance-concern flagging (licensing conflicts, territory-clearance
  doubts, piracy), and deliverable handoff coordination to exhibitors
  and platforms. It NEVER directly finalizes a rights-licensing grant
  or a distribution-window/territory-clearance decision -- see
  `filmdistops.governor`'s `scope-exclusion-violations`, a HARD,
  permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `titles` directory keyed by `:title-id` STRING (never a
  keyword -- consistent keying from the start, avoiding the silent-miss
  bug that plagued an earlier shepherd attempt).

  A registered/verified title-contract record must exist before ANY
  proposal for that title may ever commit or escalate --
  `filmdistops.governor`'s `title-unverified-violations` re-derives this
  from the title's own `:registered?`/`:verified?` fields, never from
  proposal self-report, the SAME 'ground truth, not self-report'
  discipline every sibling actor's own governor uses.

  The ledger stays append-only: which title a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by
  whom is always a query over an immutable log.")

(defprotocol Store
  (title [s title-id] "Registered title/contract record, or nil.
    Title map: {:title-id .. :name .. :registered? bool :verified? bool}.")
  (all-titles [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (distribution-log [s] "the append-only committed distribution-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-titles [s titles] "replace/seed the title directory (map title-id->title)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained title directory covering both the happy path
  and the governor's own hard checks, so the actor + tests run offline."
  []
  {:titles
   {"title-1" {:title-id "title-1" :name "The Long Horizon (feature, 2026)"
               :registered? true :verified? true}
    "title-2" {:title-id "title-2" :name "Late Autumn Signal (TV series, S1)"
               :registered? true :verified? true}
    "title-3" {:title-id "title-3" :name "Paper Kites Over Kanda (feature, in intake)"
               :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (title [_ title-id] (get-in @a [:titles title-id]))
  (all-titles [_] (sort-by :title-id (vals (:titles @a))))
  (ledger [_] (:ledger @a))
  (distribution-log [_] (:distribution-log @a))
  (commit-record! [_ record]
    (swap! a update :distribution-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-titles [s titles] (when (seq titles) (swap! a assoc :titles titles)) s))

(defn seed-db
  "A MemStore seeded with the demo title directory. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :distribution-log []))))

(defn mem-store
  "A MemStore seeded with an explicit `titles` map (title-id string ->
  title map) -- the primary test/dev entry point. `titles` may be empty
  (an unregistered-everywhere store)."
  [titles]
  (->MemStore (atom {:titles (or titles {}) :ledger [] :distribution-log []})))
