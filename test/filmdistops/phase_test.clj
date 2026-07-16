(ns filmdistops.phase-test
  "Unit tests of `filmdistops.phase` rollout logic."
  (:require [clojure.test :refer [deftest is testing]]
            [filmdistops.phase :as phase]))

(def clean-verdict {:hard? false :escalate? false})
(def low-conf-verdict {:hard? false :escalate? true})
(def hard-verdict {:hard? true :escalate? false})

(deftest phase-0-read-only
  (testing "phase 0 allows no writes"
    (doseq [op [:log-distribution-record :schedule-release-operation
                :flag-rights-concern :coordinate-delivery]]
      (let [{:keys [disposition]} (phase/gate 0 {:op op} :commit)]
        (is (= :hold disposition)
            (str "phase 0 must hold all ops including " op))))))

(deftest phase-1-distribution-record-only
  (testing "phase 1 allows only distribution-record logging, requires approval"
    (let [{:keys [disposition reason]} (phase/gate 1 {:op :log-distribution-record} :commit)]
      (is (= :escalate disposition))
      (is (= :phase-approval reason)))
    (let [{:keys [disposition]} (phase/gate 1 {:op :schedule-release-operation} :commit)]
      (is (= :hold disposition)))))

(deftest phase-2-adds-coordination-ops
  (testing "phase 2 allows coordination ops, still requires approval"
    (doseq [op [:log-distribution-record :schedule-release-operation :coordinate-delivery]]
      (let [{:keys [disposition]} (phase/gate 2 {:op op} :commit)]
        (is (= :escalate disposition)
            (str "phase 2 op " op " requires approval"))))))

(deftest phase-3-auto-commits-clean-ops
  (testing "phase 3 auto-commits clean, high-conf non-rights-concern ops"
    (let [{:keys [disposition]} (phase/gate 3 {:op :log-distribution-record} :commit)]
      (is (= :commit disposition)))
    (let [{:keys [disposition]} (phase/gate 3 {:op :schedule-release-operation} :commit)]
      (is (= :commit disposition)))
    (let [{:keys [disposition]} (phase/gate 3 {:op :coordinate-delivery} :commit)]
      (is (= :commit disposition)))))

(deftest rights-concern-holds-when-not-enabled
  (testing ":flag-rights-concern holds in phases 0-2 (not yet enabled)"
    (doseq [ph [0 1 2]]
      (let [{:keys [disposition]} (phase/gate ph {:op :flag-rights-concern} :escalate)]
        (is (= :hold disposition)
            (str "phase " ph " has not enabled flag-rights-concern yet"))))))

(deftest rights-concern-escalates-when-enabled
  (testing ":flag-rights-concern ALWAYS escalates when enabled, even if governor says commit"
    (let [{:keys [disposition]} (phase/gate 3 {:op :flag-rights-concern} :commit)]
      (is (= :escalate disposition)
          "phase 3 must escalate rights concerns regardless of governor disposition"))))

(deftest hard-hold-always-wins
  (testing "a governor HARD hold stays HOLD regardless of phase"
    (doseq [ph [0 1 2 3]]
      (let [{:keys [disposition]} (phase/gate ph {:op :log-distribution-record} :hold)]
        (is (= :hold disposition)
            (str "phase " ph " must respect governor HARD hold"))))))

(deftest verdict->disposition-maps-correctly
  (testing "verdict->disposition correctly translates governor verdict to base disposition"
    (is (= :hold (phase/verdict->disposition {:hard? true :escalate? false})))
    (is (= :escalate (phase/verdict->disposition {:hard? false :escalate? true})))
    (is (= :commit (phase/verdict->disposition {:hard? false :escalate? false})))))
