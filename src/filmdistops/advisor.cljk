(ns filmdistops.advisor
  "FilmDistAdvisor -- the *contained intelligence node* for the
  ISIC-5913 motion-picture/video/TV-programme distribution
  operations-coordination actor.

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: distribution-record logging, release/marketing-window
  scheduling, rights/clearance-concern flagging, and delivery-handoff
  coordination. CRITICAL: it is a smart-but-untrusted advisor. It
  returns a *proposal* (with a rationale + the fields it cited), never
  a committed record and NEVER a direct actuation -- every proposal's
  `:effect` is always `:propose`. Every output is censored downstream
  by `filmdistops.governor` before anything touches the SSoT.

  This advisor NEVER finalizes a rights-licensing grant and NEVER
  finalizes a distribution-window or territory-clearance decision --
  those are permanently out of scope for this actor, not merely
  un-implemented. `filmdistops.governor`'s `scope-exclusion-violations`
  independently re-scans every proposal for exactly this failure mode
  (a compromised or confused advisor drifting into scope it must never
  touch) and HARD-holds it, regardless of confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :title-id   str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-distribution-record
  "Draft a title/territory/window distribution-record log entry. Pure
  metadata logging (title, territory, window dates) -- never a
  rights-licensing or window-clearance decision."
  [_db {:keys [title-id patch]}]
  {:op         :log-distribution-record
   :title-id   title-id
   :summary    (str title-id " のディストリビューション記録を記録: " (pr-str (keys patch)))
   :rationale  "作品・地域・配信ウィンドウのメタデータ記録のみ。権利許諾や配給ウィンドウの可否とは無関係。"
   :cites      [title-id]
   :effect     :propose
   :value      (merge {:title-id title-id} patch)
   :confidence 0.93})

(defn- propose-release-operation
  "Draft a release-date/marketing-window scheduling proposal (an
  internal ops calendar entry, never a binding distribution-window
  clearance decision)."
  [_db {:keys [title-id patch]}]
  {:op         :schedule-release-operation
   :title-id   title-id
   :summary    (str title-id " の公開日程・マーケティングウィンドウ調整を提案: " (pr-str (keys patch)))
   :rationale  "公開日・マーケティングウィンドウの社内調整提案のみ。配給ウィンドウの可否を決めるものではない。"
   :cites      [title-id]
   :effect     :propose
   :value      (merge {:title-id title-id} patch)
   :confidence 0.88})

(defn- propose-rights-concern
  "Surface a rights/licensing-conflict, territory-clearance doubt, or
  piracy concern for HUMAN triage. This op ALWAYS escalates in
  `filmdistops.governor` -- never auto-committed at any phase --
  regardless of how confident the advisor is that the concern is real."
  [_db {:keys [title-id patch]}]
  {:op         :flag-rights-concern
   :title-id   title-id
   :summary    (str title-id " の権利懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "権利抵触・地域クリアランスの疑義・海賊版行為に関する観察事実の報告。可否判断は常に人間が行う。"
   :cites      [title-id]
   :effect     :propose
   :value      (merge {:title-id title-id} patch)
   :confidence (or (:confidence patch) 0.85)})

(defn- propose-delivery-coordination
  "Draft a deliverable handoff coordination proposal to an exhibitor
  or platform (technical/logistics coordination only, never a rights
  grant or clearance decision)."
  [_db {:keys [title-id patch]}]
  {:op         :coordinate-delivery
   :title-id   title-id
   :summary    (str title-id " の配給先納品ハンドオフ調整を提案: " (pr-str (keys patch)))
   :rationale  "配給先(劇場/配信プラットフォーム)への技術的納品物ハンドオフ調整のみ。権利許諾の可否とは無関係。"
   :cites      [title-id]
   :effect     :propose
   :value      (merge {:title-id title-id} patch)
   :confidence 0.90})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-distribution-record (propose-distribution-record _db request)
                   :schedule-release-operation (propose-release-operation _db request)
                   :flag-rights-concern (propose-rights-concern _db request)
                   :coordinate-delivery (propose-delivery-coordination _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually decided to finalize the rights license and grant distribution rights")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t       :advisor-proposal
   :op      (:op proposal)
   :title-id (:title-id proposal)
   :summary (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
