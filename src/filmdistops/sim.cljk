(ns filmdistops.sim
  "Demo driver -- `clojure -M:run`. Walks a clean distribution-record
  logging request through intake -> advise -> govern -> decide ->
  approval -> commit at phase 1 (assisted-logging, always approval),
  then re-runs the same op at phase 3 (supervised-auto, clean + high
  confidence -> auto-commit), then a release-scheduling request,
  delivery-coordination request (both auto-commit clean at phase 3),
  then a rights-concern flag (ALWAYS escalates, at any phase --
  approve, then commit), then HARD-hold scenarios: an unregistered
  title, a title registered but not yet verified, a proposal whose own
  `:effect` is not `:propose`, and a proposal that has drifted into
  the permanently-excluded rights-licensing-grant-finalization/
  distribution-window-clearance-finalization scope."
  (:require [langgraph.graph :as g]
            [filmdistops.advisor :as advisor]
            [filmdistops.store :as store]
            [filmdistops.operation :as op]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "distribution-coordinator-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        coordinator-phase-1 {:actor-id "coord-1" :actor-role :distribution-coordinator :phase 1}
        coordinator-phase-3 {:actor-id "coord-1" :actor-role :distribution-coordinator :phase 3}
        actor (op/build db)]

    (println "== log-distribution-record title-1 (phase 1, escalates -- human approves) ==")
    (let [r (exec-op actor "t1" {:op :log-distribution-record :title-id "title-1"
                                  :patch {:territory "JP" :window "theatrical"}} coordinator-phase-1)]
      (println r)
      (println "-- human distribution coordinator approves --")
      (println (approve! actor "t1")))

    (println "\n== log-distribution-record title-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t2" {:op :log-distribution-record :title-id "title-1"
                                  :patch {:territory "US" :window "svod"}} coordinator-phase-3))

    (println "\n== schedule-release-operation title-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t3" {:op :schedule-release-operation :title-id "title-1"
                                  :patch {:release-date "2026-09-18" :marketing-window "6-weeks-pre"}} coordinator-phase-3))

    (println "\n== coordinate-delivery title-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t4" {:op :coordinate-delivery :title-id "title-1"
                                  :patch {:exhibitor "Kanda Cinema Group" :deliverable "DCP" :due "2026-09-01"}} coordinator-phase-3))

    (println "\n== flag-rights-concern title-1 (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t5" {:op :flag-rights-concern :title-id "title-1"
                                 :patch {:concern "possible territory overlap with an existing SVOD deal" :confidence 0.9}} coordinator-phase-3)]
      (println r)
      (println "-- human distribution coordinator reviews & approves --")
      (println (approve! actor "t5")))

    (println "\n== log-distribution-record title-99 (unregistered title -> HARD hold) ==")
    (println (exec-op actor "t6" {:op :log-distribution-record :title-id "title-99"
                                  :patch {:territory "DE"}} coordinator-phase-3))

    (println "\n== log-distribution-record title-3 (registered but unverified -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :log-distribution-record :title-id "title-3"
                                  :patch {:territory "FR"}} coordinator-phase-3))

    (println "\n== schedule-release-operation title-1, advisor attempts direct actuation (:effect :commit) -> HARD hold ==")
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (println (exec-op actor-direct "t8" {:op :schedule-release-operation :title-id "title-1"
                                           :patch {:release-date "2026-10-01"}} coordinator-phase-3)))

    (println "\n== log-distribution-record title-1, advisor drifts into rights/window-clearance-finalization scope -> HARD hold, permanent ==")
    (println (exec-op actor "t9" {:op :log-distribution-record :title-id "title-1"
                                   :out-of-scope? true
                                   :patch {}} coordinator-phase-3))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "\n== committed distribution log ==")
    (doseq [r (store/distribution-log db)] (println r))))
