(ns filmdistops.governor
  "FilmDistGovernor -- the independent compliance layer that earns
  the FilmDistAdvisor the right to commit. The advisor has no notion
  of whether a title/contract record is actually registered and
  verified, whether its own proposed `:effect` secretly claims a
  direct actuation instead of a mere proposal, or whether it has
  silently drifted into a permanently out-of-scope decision area, so
  this MUST be a separate system able to *reject* a proposal and fall
  back to HOLD.

  This actor's scope is deliberately narrow -- DISTRIBUTION OPERATIONS
  COORDINATION ONLY (distribution-record logging, release/marketing-
  window scheduling proposals, rights/clearance-concern flagging,
  delivery-handoff coordination). It NEVER performs or authorizes:
    - finalizing a rights-licensing grant (who is licensed to
      distribute a title, in what territory, under what terms)
    - finalizing a distribution-window or territory-clearance decision
      (locking in which window/territory a title actually releases in)

  Both of those are ALWAYS either a hard permanent block (this
  governor) or an always-escalate op (`:flag-rights-concern`) --
  NEVER an auto-commit-eligible op in any phase. This actor coordinates
  the back office around those decisions; it never makes them.

  Two HARD checks, ALL permanent, un-overridable by any human approval:

    1. Title unverified          -- the target title/contract record
                                     must exist AND be independently
                                     confirmed `:registered?`/
                                     `:verified?` in the store before
                                     ANY proposal for it may commit or
                                     even escalate. Never trusts a
                                     proposal's own claim about the
                                     title -- re-derived from the
                                     title's own store record, the same
                                     'ground truth, not self-report'
                                     discipline every sibling actor's
                                     governor uses.
    2. Effect not :propose       -- every proposal's `:effect` MUST
                                     be `:propose`. Any other effect
                                     value is, by construction, a
                                     claim to directly actuate/commit
                                     outside governance -- HARD block,
                                     not merely low-confidence.
    3. Scope exclusion           -- ANY proposal (regardless of op)
                                     whose op, rationale, summary,
                                     citations or draft value touches
                                     the ACT of finalizing a
                                     rights-licensing grant, or the
                                     ACT of finalizing a
                                     distribution-window/territory-
                                     clearance decision, is a HARD,
                                     PERMANENT block -- this actor's
                                     charter excludes that territory
                                     structurally, not as a rollout
                                     milestone. Evaluated
                                     UNCONDITIONALLY on every
                                     proposal. An op outside the
                                     closed four-op allowlist is the
                                     SAME failure mode (an advisor
                                     proposing something it was never
                                     authorized to propose) and is
                                     folded into this same check.

  IMPORTANT (self-trip discipline): `scope-excluded-terms` below are
  phrased as the FINALIZATION/EXECUTION ACTION ('finalize the rights
  license', 'clear the distribution window'), never as a bare noun
  ('rights', 'license', 'window', 'territory', 'clearance'). This
  actor's own legitimate happy-path proposals -- especially
  `:flag-rights-concern`, whose entire purpose is to talk ABOUT rights/
  clearance concerns -- routinely use those bare nouns in their default
  rationale text. A bare-noun term list would self-trip the actor on
  its own default mock-advisor proposals; `governor-test` and
  `governor-contract-test` both assert this never happens.

  One ESCALATE (SOFT) gate: LLM confidence below the floor, OR the op
  is `:flag-rights-concern` -- ALWAYS escalates to a human, regardless
  of confidence, regardless of how clean the proposal otherwise is.
  `filmdistops.phase` independently agrees: `:flag-rights-concern` is
  never a member of any phase's `:auto` set either -- two layers, not
  one."
  (:require [clojure.string :as str]
            [filmdistops.store :as store]))

(def confidence-floor 0.6)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a
  scope violation by construction (see `scope-exclusion-violations`)."
  #{:log-distribution-record :schedule-release-operation
    :flag-rights-concern :coordinate-delivery})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-rights-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as attempting to
  directly FINALIZE a rights-licensing grant or a distribution-window/
  territory-clearance decision -- this actor's two permanently
  out-of-scope decision areas. Phrased as the finalization/execution
  ACTION, never as a bare noun, so this list never matches inside this
  actor's own legitimate proposals (which routinely discuss rights/
  clearance/window/territory as topics without ever finalizing them).
  Scanned across the proposal's op/summary/rationale/cites/value,
  never trusting the advisor's own framing of its intent."
  ["finalize the rights license" "finalize rights license"
   "finalize licensing grant" "finalize the licensing grant"
   "grant the distribution rights" "grant distribution rights"
   "grant the license" "grant licensing rights"
   "authorize the rights grant" "authorize licensing grant"
   "execute the rights license" "sign the licensing agreement"
   "権利許諾を確定" "ライセンス許諾を確定" "配給権を許諾する決定"
   "権利許諾の決定を下す" "権利許諾を許諾する"
   "finalize the distribution window" "finalize window clearance"
   "finalize the window clearance" "clear the distribution window"
   "clear the territory window" "confirm window clearance"
   "authorize window clearance" "lock the release window decision"
   "finalize the clearance decision" "finalize territory clearance"
   "配給ウィンドウを確定" "ウィンドウクリアランスを決定"
   "公開ウィンドウの確定決定" "地域クリアランスを確定"
   "テリトリークリアランスを確定"])

;; ----------------------------- checks -----------------------------

(defn- title-unverified-violations
  "The target title/contract record must exist AND be independently
  `:registered?`/`:verified?` in the store -- never trust the
  proposal's own `:title-id` claim without a store lookup."
  [{:keys [title-id]} st]
  (let [r (store/title st title-id)]
    (when-not (and r (:registered? r) (:verified? r))
      [{:rule :title-unverified
        :detail (str title-id " は未登録または未検証のタイトル/契約 -- いかなる提案も進められない")}])))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim
  to directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one
  lower-cased blob the scope-exclusion scan checks."
  [proposal]
  (str/lower-case (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist,
  or one whose content touches finalizing a rights-licensing grant or
  finalizing a distribution-window/territory-clearance decision,
  regardless of confidence or how clean every other check is.
  Evaluated UNCONDITIONALLY on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "権利許諾の付与確定/配給ウィンドウ・地域クリアランスの確定判断に踏み込む提案は永久に禁止"}])))

(defn check
  "Censors a FilmDistAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [title-id (or (:title-id proposal) (:title-id request))
        hard (into []
                   (concat (title-unverified-violations {:title-id title-id} store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (always-escalate-ops (:op proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :title-id   (:title-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
