(ns filmdistops.governor-test
  "Pure unit tests of `filmdistops.governor/check` against hand-built
  proposals -- the fast, focused complement to `governor-contract-test`'s
  full-graph integration coverage."
  (:require [clojure.test :refer [deftest is testing]]
            [filmdistops.advisor :as advisor]
            [filmdistops.governor :as gov]
            [filmdistops.store :as store]))

(def title-1 {:title-id "title-1" :name "The Long Horizon" :registered? true :verified? true})
(def title-3 {:title-id "title-3" :name "Paper Kites Over Kanda" :registered? true :verified? false})

(defn- clean-proposal [op title-id]
  {:op op :title-id title-id :summary "s" :rationale "routine distribution coordination"
   :cites [title-id] :effect :propose :value {} :confidence 0.85})

(deftest title-unregistered-is-hard
  (testing "no title record at all -> HARD hold"
    (let [s (store/mem-store {"title-1" title-1})
          verdict (gov/check {} nil (clean-proposal :log-distribution-record "unknown-title") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:title-unverified} (map :rule (:violations verdict)))))))

(deftest title-unverified-is-hard
  (testing "title registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"title-3" title-3})
          verdict (gov/check {} nil (clean-proposal :log-distribution-record "title-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:title-unverified} (map :rule (:violations verdict)))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"title-1" title-1})
          verdict (gov/check {} nil (assoc (clean-proposal :schedule-release-operation "title-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed four-op allowlist is a scope violation"
    (let [s (store/mem-store {"title-1" title-1})
          verdict (gov/check {} nil (clean-proposal :grant-rights-license "title-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest rights-license-finalization-content-is-hard-and-permanent
  (testing "a proposal whose rationale finalizes a rights-licensing grant is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"title-1" title-1})
          poisoned (assoc (clean-proposal :log-distribution-record "title-1")
                          :rationale "decided to finalize the rights license and grant distribution rights to territory JP"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest window-clearance-finalization-content-is-hard
  (testing "a proposal touching finalizing a distribution-window clearance decision is HARD-blocked, same as rights"
    (let [s (store/mem-store {"title-1" title-1})
          poisoned (assoc (clean-proposal :schedule-release-operation "title-1")
                          :rationale "decided to clear the distribution window and finalize window clearance for Q4"
                          :confidence 0.90)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest territory-clearance-finalization-content-is-hard
  (testing "a proposal touching finalizing territory clearance is HARD-blocked"
    (let [s (store/mem-store {"title-1" title-1})
          poisoned (assoc (clean-proposal :coordinate-delivery "title-1")
                          :summary "finalize territory clearance ahead of the exhibitor handoff")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest license-grant-content-in-value-is-hard
  (testing "a proposal whose draft value grants the license is HARD-blocked"
    (let [s (store/mem-store {"title-1" title-1})
          poisoned (assoc (clean-proposal :log-distribution-record "title-1")
                          :value {:decision "grant the license to Kanda Cinema Group"})
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legitimate-rights-concern-is-not-scope-excluded
  (testing "flagging a possible territory/rights conflict as a RIGHTS CONCERN (not a finalization) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"title-1" title-1})
          concern (assoc (clean-proposal :flag-rights-concern "title-1")
                         :value {:concern "possible territory overlap with an existing SVOD deal"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw observation content (territory/rights doubts) is exactly what this op exists to surface"))))

;; ----------------------------- self-trip regression (mandatory) -----------------------------
;;
;; A known bug class in this exact codebase family: a governor's own
;; scope-exclusion term list phrased as a bare noun can accidentally
;; match inside the mock advisor's own DEFAULT rationale/disclaimer
;; text for a legitimate, allowed proposal -- causing the actor to
;; self-block on its own happy path. This actor's `scope-excluded-terms`
;; are deliberately phrased as the finalization/execution ACTION
;; ('finalize the rights license', not bare 'rights license'). This
;; test asserts the default mock advisor's own proposals for all four
;; allowed ops, for a clean registered+verified title, NEVER trip
;; scope-exclusion -- i.e. the actor never self-blocks on its own
;; happy path.
(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "none of the four default proposal generators' own rationale/summary/value text self-trips scope-exclusion"
    (let [s (store/mem-store {"title-1" title-1})]
      (doseq [op [:log-distribution-record :schedule-release-operation
                  :flag-rights-concern :coordinate-delivery]]
        (let [proposal (advisor/infer nil {:op op :title-id "title-1"
                                            :patch {:territory "JP" :window "theatrical"
                                                    :release-date "2026-09-18"
                                                    :exhibitor "Kanda Cinema Group"
                                                    :deliverable "DCP"
                                                    :concern "possible territory overlap"}})
              verdict (gov/check {:title-id "title-1"} nil proposal s)]
          (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
              (str "default proposal for op " op " must never self-trip scope-exclusion; got violations: "
                   (:violations verdict)))
          (is (not (:hard? verdict))
              (str "default proposal for op " op " (clean, registered+verified title) must never HARD hold")))))))
