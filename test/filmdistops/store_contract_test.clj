(ns filmdistops.store-contract-test
  "Contract tests for `filmdistops.store/Store` protocol."
  (:require [clojure.test :refer [deftest is testing]]
            [filmdistops.store :as store]))

(deftest mem-store-title-lookup
  (testing "MemStore can store and retrieve titles by ID (string keys)"
    (let [titles {"t1" {:title-id "t1" :name "Alice's Reel" :registered? true :verified? true}}
          s (store/mem-store titles)]
      (is (some? (store/title s "t1")))
      (is (nil? (store/title s "t99"))))))

(deftest mem-store-all-titles
  (testing "MemStore returns all titles in sorted order"
    (let [titles {"t2" {:title-id "t2" :name "Bob's Doc"}
                  "t1" {:title-id "t1" :name "Alice's Reel"}
                  "t3" {:title-id "t3" :name "Carol's Series"}}
          s (store/mem-store titles)
          all-t (store/all-titles s)]
      (is (= 3 (count all-t)))
      (is (= "t1" (:title-id (first all-t))))
      (is (= "t3" (:title-id (last all-t)))))))

(deftest mem-store-ledger-append
  (testing "MemStore append-ledger! adds facts to immutable log"
    (let [s (store/mem-store {})
          fact1 {:t :test :data "fact1"}
          fact2 {:t :test :data "fact2"}]
      (is (= 0 (count (store/ledger s))))
      (store/append-ledger! s fact1)
      (is (= 1 (count (store/ledger s))))
      (store/append-ledger! s fact2)
      (is (= 2 (count (store/ledger s)))))))

(deftest mem-store-distribution-log
  (testing "MemStore commit-record! appends to distribution-log"
    (let [s (store/mem-store {})
          record {:op :log-distribution-record :title-id "t1" :value {:territory "JP"}}]
      (is (= 0 (count (store/distribution-log s))))
      (store/commit-record! s record)
      (is (= 1 (count (store/distribution-log s))))
      (is (= record (first (store/distribution-log s)))))))

(deftest mem-store-with-titles
  (testing "MemStore with-titles replaces the title directory"
    (let [s (store/mem-store {})
          new-titles {"t1" {:title-id "t1" :name "Alice's Reel"}}]
      (is (= 0 (count (store/all-titles s))))
      (store/with-titles s new-titles)
      (is (= 1 (count (store/all-titles s)))))))

(deftest seed-db-has-demo-data
  (testing "seed-db creates a populated MemStore with demo titles"
    (let [s (store/seed-db)]
      (is (> (count (store/all-titles s)) 0))
      (is (some? (store/title s "title-1")))
      (is (some? (store/title s "title-2")))
      (is (some? (store/title s "title-3"))))))

(deftest demo-data-string-key-consistency
  (testing "demo-data uses string keys, not keywords, for title-id"
    (let [demo (store/demo-data)
          titles (:titles demo)]
      (doseq [[k v] titles]
        (is (string? k) "keys must be strings")
        (is (string? (:title-id v)) "title-id must be string")
        (is (= k (:title-id v)) "key must match title-id")))))

(deftest store-is-append-only
  (testing "appended facts are immutable and never removed"
    (let [s (store/seed-db)
          fact1 {:t :event1 :data "a"}
          fact2 {:t :event2 :data "b"}]
      (store/append-ledger! s fact1)
      (let [ledger-after-1 (store/ledger s)]
        (store/append-ledger! s fact2)
        (let [ledger-after-2 (store/ledger s)]
          (is (= (count ledger-after-1) (dec (count ledger-after-2))))
          (is (every? #(some (fn [x] (= x %)) ledger-after-2) ledger-after-1)
              "all prior facts must still be present"))))))
