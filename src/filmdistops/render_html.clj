(ns filmdistops.render-html
  "Build-time HTML renderer for docs/samples/operator-console.html.
  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300).
  Drives the REAL actor stack (filmdistops.operation -> filmdistops.governor
  -> filmdistops.store) through langgraph-clj's StateGraph runner -- every
  row on this page is read back from a real graph-run result or the real
  store, never hand-typed. No invented numbers, no timestamps, byte-
  identical across reruns.

  Scenario (all ids/ops are real filmdistops.store/filmdistops.governor
  values -- see run-demo! below):
    - t1/t2/t3: one auto-commit-eligible op per closed-allowlist member
      that CAN auto-commit at phase 3 (:log-distribution-record,
      :schedule-release-operation, :coordinate-delivery), governor-clean.
    - t4: :flag-rights-concern, the op that ALWAYS escalates
      (`governor/always-escalate-ops`) regardless of confidence or
      phase, followed by a human approval.
    - t5..t8: four DISTINCT HARD-hold rule names produced by
      `filmdistops.governor/check`, none of which ever reach a human:
      :title-unverified, :effect-not-propose, :scope-excluded,
      :op-not-allowed."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [filmdistops.store :as store]
            [filmdistops.operation :as op]
            [filmdistops.phase :as phase]
            [filmdistops.governor :as governor]
            [filmdistops.advisor :as advisor]
            [langgraph.graph :as g]))

;; ----------------------------- demo actors -----------------------------

(def ^:private coordinator
  "The human distribution-coordinator context every request runs under --
  phase 3 (supervised-auto), the default-phase in filmdistops.phase."
  {:actor-id "coord-1" :actor-role :distribution-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context coordinator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "coord-1"}}
          {:thread-id tid :resume? true}))

(defn- effect-commit-advisor
  "A drifted advisor that claims a direct :commit effect instead of the
  mandatory :propose -- exercises governor/check's `effect-not-propose`
  HARD check end-to-end. Same pattern filmdistops.sim uses for its own
  :effect-not-propose demonstration."
  []
  (reify advisor/Advisor
    (-advise [_ _store req] (assoc (advisor/infer nil req) :effect :commit))))

(defn- unlisted-op-advisor
  "A drifted advisor that swaps in an op outside `governor/allowed-ops`
  (the closed four-op allowlist) while leaving everything else
  (title-id, :effect :propose, rationale) governor-clean -- exercises
  the `op-not-allowed` branch of governor/check's scope-exclusion check
  in isolation from the `scope-excluded` (rationale-text) branch."
  []
  (reify advisor/Advisor
    (-advise [_ _store req] (assoc (advisor/infer nil req) :op :direct-rights-grant))))

;; ----------------------------- run the real actor -----------------------------

(defn run-demo!
  "Drives the real filmdistops.operation StateGraph against a real seeded
  MemStore. Returns {:db .. :transactions [..]} where each transaction is
  {:tid :request :result :approval-result} captured directly from
  `langgraph.graph/run*` -- nothing here is synthesized after the fact."
  []
  (let [db (store/seed-db)
        actor (op/build db)
        actor-effect-commit (op/build db {:advisor (effect-commit-advisor)})
        actor-op-not-allowed (op/build db {:advisor (unlisted-op-advisor)})
        txs (atom [])
        run! (fn [tid the-actor request]
               (let [result (exec! the-actor tid request)]
                 (swap! txs conj {:tid tid :request request :result result :actor the-actor})
                 result))
        approve-last! (fn [tid the-actor]
                         (let [ares (approve! the-actor tid)]
                           (swap! txs update (dec (count @txs)) assoc :approval-result ares)
                           ares))]

    ;; -- auto-commit clean, one per phase-3 auto-eligible op --
    (run! "t1" actor {:op :log-distribution-record :title-id "title-1"
                       :patch {:territory "JP" :window "theatrical"}})
    (run! "t2" actor {:op :schedule-release-operation :title-id "title-2"
                       :patch {:release-date "2026-09-18" :marketing-window "6-weeks-pre"}})
    (run! "t3" actor {:op :coordinate-delivery :title-id "title-1"
                       :patch {:exhibitor "Kanda Cinema Group" :deliverable "DCP" :due "2026-09-01"}})

    ;; -- always-escalate op, then human approval --
    (run! "t4" actor {:op :flag-rights-concern :title-id "title-2"
                       :patch {:concern "possible territory overlap with an existing SVOD deal"
                               :confidence 0.9}})
    (approve-last! "t4" actor)

    ;; -- four DISTINCT HARD-hold rules, none reach a human --
    (run! "t5" actor {:op :log-distribution-record :title-id "title-99"
                       :patch {:territory "DE"}})                                   ; :title-unverified
    (run! "t6" actor-effect-commit {:op :schedule-release-operation :title-id "title-1"
                                     :patch {:release-date "2026-10-01"}})           ; :effect-not-propose
    (run! "t7" actor {:op :log-distribution-record :title-id "title-1" :out-of-scope? true
                       :patch {}})                                                  ; :scope-excluded
    (run! "t8" actor-op-not-allowed {:op :coordinate-delivery :title-id "title-1"
                                      :patch {:exhibitor "Riverside Multiplex" :deliverable "IMF"
                                              :due "2026-09-10"}})                   ; :op-not-allowed

    {:db db :transactions @txs}))

;; ----------------------------- rendering helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- last-fact-for
  "Most recent SSoT ledger fact for `title-id`. filmdistops uses
  :title-id (a STRING, per filmdistops.store) as its subject-key field --
  never :subject."
  [ledger title-id]
  (last (filter #(= title-id (:title-id %)) ledger)))

(defn- status-cell
  "Classify a store/ledger fact (only ever :committed or :governor-hold --
  :commit and :hold are the only two nodes in filmdistops.operation that
  call store/append-ledger!) into a semantic {ok|warn|err|muted} class."
  [fact]
  (cond
    (nil? fact) ["muted" "in progress"]
    (= :committed (:t fact)) ["ok" "committed"]
    (= :approval-granted (:t fact)) ["ok" "approval-granted"]
    (= :governor-hold (:t fact)) ["err" (str "governor-hold: " (str/join "," (map name (:basis fact))))]
    (= :approval-rejected (:t fact)) ["err" "approval-rejected"]
    (= :approval-requested (:t fact)) ["warn" "approval-requested"]
    :else ["muted" "in progress"]))

(defn- tx-final-state
  "The last real graph state for a transaction -- the approval-result
  state if the request was resumed after an interrupt, else the initial
  result state."
  [{:keys [result approval-result]}]
  (:state (or approval-result result)))

(defn- tx-audit
  "Every real :audit fact this transaction produced, across both the
  initial run and (if present) the resumed approval run -- concatenated
  in the order langgraph actually emitted them."
  [{:keys [result approval-result]}]
  (into (get-in result [:state :audit] [])
        (get-in approval-result [:state :audit] [])))

(defn- tx-row-status
  [tx]
  (let [final (tx-final-state tx)
        audit (tx-audit tx)
        disp (:disposition final)]
    (cond
      (and (= :commit disp) (some #(= :approval-granted (:t %)) audit))
      ["ok" "approval-granted -> committed"]

      (= :commit disp)
      ["ok" "committed"]

      (and (= :hold disp) (some #(= :approval-rejected (:t %)) audit))
      ["err" "approval-rejected"]

      (= :hold disp)
      (let [hf (last (filter #(= :governor-hold (:t %)) audit))]
        ["err" (str "governor-hold: " (str/join "," (map name (:basis hf))) " -- "
                     (str/join "; " (map :detail (:violations hf))))])

      (= :escalate disp) ["warn" "approval-requested (awaiting human)"]
      :else ["muted" "in progress"])))

;; ----------------------------- table builders -----------------------------

(defn- title-directory-rows [db]
  (let [ledger (store/ledger db)]
    (for [t (store/all-titles db)]
      (let [fact (last-fact-for ledger (:title-id t))
            [cls label] (status-cell fact)]
        (str "<tr>"
             "<td><code>" (esc (:title-id t)) "</code></td>"
             "<td>" (esc (:name t)) "</td>"
             "<td>" (if (:registered? t) "yes" "no") "</td>"
             "<td>" (if (:verified? t) "yes" "no") "</td>"
             "<td class=\"" cls "\">" (esc label) "</td>"
             "</tr>")))))

(defn- action-gate-rows []
  (for [op (sort (map name governor/allowed-ops))]
    (let [op-kw (keyword op)]
      (str "<tr>"
           "<td><code>" (esc op) "</code></td>"
           "<td>" (if (governor/always-escalate-ops op-kw) "yes (governor + phase, permanent)" "no") "</td>"
           (str/join
            (for [p (sort (keys phase/phases))]
              (let [{:keys [writes auto]} (get phase/phases p)
                    [cls label] (cond
                                  (not (contains? writes op-kw)) ["muted" "disabled"]
                                  (governor/always-escalate-ops op-kw) ["warn" "escalate"]
                                  (contains? auto op-kw) ["ok" "auto-commit"]
                                  :else ["warn" "escalate"])]
                (str "<td class=\"" cls "\">" label "</td>"))))
           "</tr>"))))

(defn- transaction-rows [transactions]
  (for [tx transactions]
    (let [{:keys [tid request]} tx
          [cls label] (tx-row-status tx)
          confidence (get-in (tx-final-state tx) [:verdict :confidence])]
      (str "<tr>"
           "<td><code>" (esc tid) "</code></td>"
           "<td><code>" (esc (name (:op request))) "</code></td>"
           "<td><code>" (esc (:title-id request)) "</code></td>"
           "<td>" (if confidence (format "%.2f" (double confidence)) "-") "</td>"
           "<td class=\"" cls "\">" (esc label) "</td>"
           "</tr>"))))

(defn- ledger-rows [db]
  (for [f (store/ledger db)]
    (let [[cls label] (status-cell f)]
      (str "<tr>"
           "<td class=\"" cls "\">" (esc label) "</td>"
           "<td><code>" (esc (name (:op f))) "</code></td>"
           "<td><code>" (esc (:title-id f)) "</code></td>"
           "<td>" (esc (:actor f)) "</td>"
           "<td>" (esc (or (:summary f)
                            (str/join "; " (map :detail (:violations f)))))
           "</td>"
           "</tr>"))))

(defn- distribution-log-rows [db]
  (for [r (store/distribution-log db)]
    (str "<tr>"
         "<td><code>" (esc (name (:op r))) "</code></td>"
         "<td><code>" (esc (:title-id r)) "</code></td>"
         "<td><code>" (esc (pr-str (:payload r))) "</code></td>"
         "</tr>")))

;; ----------------------------- page -----------------------------

(def ^:private css
  "
  :root { color-scheme: light dark; }
  body { font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", sans-serif;
         max-width: 72rem; margin: 2rem auto; padding: 0 1.5rem; line-height: 1.5; }
  h1 { font-size: 1.5rem; margin-bottom: 0.25rem; }
  h2 { font-size: 1.1rem; margin-top: 2.5rem; border-bottom: 1px solid #8884; padding-bottom: 0.25rem; }
  .meta { color: #666; font-size: 0.9rem; }
  table { width: 100%; border-collapse: collapse; margin-top: 0.75rem; font-size: 0.92rem; }
  th, td { text-align: left; padding: 0.4rem 0.6rem; border-bottom: 1px solid #8883; vertical-align: top; }
  th { font-weight: 600; color: #555; }
  code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.88em; }
  .ok { color: #1a7f37; font-weight: 600; }
  .warn { color: #9a6700; font-weight: 600; }
  .err { color: #cf222e; font-weight: 600; }
  .critical { color: #cf222e; font-weight: 700; text-decoration: underline; }
  .muted { color: #888; }
  footer { margin-top: 3rem; padding-top: 1rem; border-top: 1px solid #8884; color: #888; font-size: 0.85rem; }
  @media (prefers-color-scheme: dark) {
    body { background: #0d1117; color: #c9d1d9; }
    th { color: #8b949e; }
    .meta, footer { color: #8b949e; }
    .muted { color: #6e7681; }
  }
  ")

(defn render [{:keys [db transactions]}]
  (str
   "<!doctype html>\n<html lang=\"en\">\n<head>\n"
   "<meta charset=\"utf-8\">\n"
   "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
   "<title>filmdistops operator console</title>\n"
   "<style>" css "</style>\n"
   "</head>\n<body>\n"
   "<h1>filmdistops operator console</h1>\n"
   "<p class=\"meta\">ISIC Rev.4 5913 -- motion picture, video and television programme "
   "distribution activities. This page is generated at build time by running the real "
   "<code>filmdistops.operation</code> langgraph-clj StateGraph "
   "(intake -&gt; advise -&gt; govern -&gt; decide -&gt; request-approval -&gt; commit/hold) "
   "against a real seeded <code>filmdistops.store</code>, via <code>filmdistops.render-html</code>. "
   "Every table below is read back from that real run or the real store -- no invented numbers.</p>\n"

   "<h2>Title / contract directory</h2>\n"
   "<p class=\"meta\">Every proposal must resolve to a store-verified title record "
   "(<code>governor/title-unverified-violations</code>) before it may commit or escalate.</p>\n"
   "<table><thead><tr><th>title-id</th><th>name</th><th>registered?</th><th>verified?</th>"
   "<th>latest ledger status</th></tr></thead><tbody>\n"
   (str/join "\n" (title-directory-rows db))
   "\n</tbody></table>\n"

   "<h2>Action gate -- closed op allowlist x rollout phase</h2>\n"
   "<p class=\"meta\">Derived from <code>filmdistops.governor/allowed-ops</code>, "
   "<code>filmdistops.governor/always-escalate-ops</code> and <code>filmdistops.phase/phases</code>. "
   "<code>:flag-rights-concern</code> never auto-commits at any phase -- two independent layers "
   "(governor + phase) agree.</p>\n"
   "<table><thead><tr><th>op</th><th>always escalates?</th>"
   (str/join (for [p (sort (keys phase/phases))] (str "<th>phase " p " (" (:label (get phase/phases p)) ")</th>")))
   "</tr></thead><tbody>\n"
   (str/join "\n" (action-gate-rows))
   "\n</tbody></table>\n"

   "<h2>Demo transactions (this run)</h2>\n"
   "<p class=\"meta\">One row per <code>langgraph.graph/run*</code> call this generator actually made "
   "against the real actor graph, in call order.</p>\n"
   "<table><thead><tr><th>thread</th><th>op</th><th>title-id</th><th>advisor confidence</th>"
   "<th>outcome</th></tr></thead><tbody>\n"
   (str/join "\n" (transaction-rows transactions))
   "\n</tbody></table>\n"

   "<h2>Audit ledger (SSoT, append-only)</h2>\n"
   "<p class=\"meta\"><code>filmdistops.store/ledger</code> after this run -- written only by the "
   "<code>:commit</code> and <code>:hold</code> nodes of <code>filmdistops.operation</code>, never by "
   "the advisor.</p>\n"
   "<table><thead><tr><th>status</th><th>op</th><th>title-id</th><th>actor</th><th>detail</th>"
   "</tr></thead><tbody>\n"
   (str/join "\n" (ledger-rows db))
   "\n</tbody></table>\n"

   "<h2>Committed distribution log</h2>\n"
   "<p class=\"meta\"><code>filmdistops.store/distribution-log</code> -- written only by "
   "<code>store/commit-record!</code>, called only from the <code>:commit</code> node.</p>\n"
   "<table><thead><tr><th>op</th><th>title-id</th><th>payload</th></tr></thead><tbody>\n"
   (str/join "\n" (distribution-log-rows db))
   "\n</tbody></table>\n"

   "<footer>Generated by <code>filmdistops.render-html</code> "
   "(com-junkawasaki/root ADR-2607189300, flagship checklist item 2) -- "
   "driving the real <code>filmdistops.operation</code> / <code>filmdistops.governor</code> / "
   "<code>filmdistops.store</code> stack, not a mockup. Regenerated nightly by "
   "<code>.github/workflows/regenerate.yml</code> (item 4); commits only when the output changes.</footer>\n"
   "</body>\n</html>\n"))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db transactions]} (run-demo!)
        html (render {:db db :transactions transactions})]
    (io/make-parents out)
    (spit out html)
    (println "wrote" out)))
