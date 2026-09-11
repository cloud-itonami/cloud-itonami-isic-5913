(ns filmdistops.advisor-test
  "Unit tests of `filmdistops.advisor` proposal generation."
  (:require [clojure.test :refer [deftest is testing]]
            [filmdistops.advisor :as adv]
            [filmdistops.store :as store]))

(def db (store/seed-db))

(deftest propose-distribution-record-shape
  (testing "distribution-record proposal has correct shape and fields"
    (let [p (adv/infer db {:op :log-distribution-record
                           :title-id "title-1"
                           :patch {:territory "JP" :window "theatrical"}})]
      (is (= :log-distribution-record (:op p)))
      (is (= "title-1" (:title-id p)))
      (is (= :propose (:effect p)))
      (is (<= 0 (:confidence p) 1))
      (is (map? (:value p)))
      (is (contains? (:value p) :title-id)))))

(deftest propose-release-operation-shape
  (testing "release-operation proposal has correct shape"
    (let [p (adv/infer db {:op :schedule-release-operation
                           :title-id "title-2"
                           :patch {:release-date "2026-09-18"}})]
      (is (= :schedule-release-operation (:op p)))
      (is (= "title-2" (:title-id p)))
      (is (= :propose (:effect p))))))

(deftest propose-rights-concern-shape
  (testing "rights-concern proposal has correct shape"
    (let [p (adv/infer db {:op :flag-rights-concern
                           :title-id "title-1"
                           :patch {:concern "territory overlap"}})]
      (is (= :flag-rights-concern (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest propose-delivery-coordination-shape
  (testing "delivery-coordination proposal has correct shape"
    (let [p (adv/infer db {:op :coordinate-delivery
                           :title-id "title-1"
                           :patch {:exhibitor "Kanda Cinema Group" :deliverable "DCP"}})]
      (is (= :coordinate-delivery (:op p)))
      (is (= :propose (:effect p)))
      (is (>= (:confidence p) 0.85)))))

(deftest all-proposals-effect-is-always-propose
  (testing "every proposal type has :effect :propose, never direct actuation"
    (doseq [op [:log-distribution-record :schedule-release-operation
                :flag-rights-concern :coordinate-delivery]]
      (let [p (adv/infer db {:op op :title-id "title-1" :patch {}})]
        (is (= :propose (:effect p))
            (str "op " op " must have :effect :propose"))))))

(deftest rationale-string-is-present
  (testing "every proposal has a rationale explaining the advisor's thinking"
    (doseq [op [:log-distribution-record :schedule-release-operation
                :flag-rights-concern :coordinate-delivery]]
      (let [p (adv/infer db {:op op :title-id "title-1" :patch {}})]
        (is (string? (:rationale p))
            (str "op " op " must have a :rationale string"))))))
