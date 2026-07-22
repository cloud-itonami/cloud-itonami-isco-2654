(ns video-production.store-contract-test
  "MemStore ≡ DatomicStore parity for the Store protocol — proves the
  backend swap (ADR-2607011000 injection boundary) is real: the same
  sequence of operations against either backend produces the same
  observable results."
  (:require [clojure.test :refer [deftest is testing]]
            [video-production.store :as store]))

(defn- exercise [s]
  (store/register-production! s {:production/id "prod-1"
                                  :subjects-consented? true
                                  :location-cleared? true})
  (store/commit-record! s {:production-id "prod-1" :op :assemble
                            :payload {:op :assemble}
                            :render-plan {:segments [{:index 0}] :bgm-blob-key "bgm-key" :width 1280}})
  (store/append-ledger! s {:disposition :commit :record {:production-id "prod-1"}})
  {:production (store/production s "prod-1")
   :records (store/records-of s "prod-1")
   :ledger (store/ledger s)})

(deftest mem-and-datomic-parity
  (testing "same operations against MemStore and DatomicStore observe the same results"
    (let [mem (exercise (store/mem-store))
          dat (exercise (store/datomic-store))]
      (is (true? (:subjects-consented? (:production mem))))
      (is (true? (:subjects-consented? (:production dat))))
      (is (= 1 (count (:records mem))))
      (is (= 1 (count (:records dat))))
      (is (= 1280 (get-in (first (:records mem)) [:render-plan :width])))
      (is (= 1280 (get-in (first (:records dat)) [:render-plan :width])))
      (is (= "bgm-key" (get-in (first (:records mem)) [:render-plan :bgm-blob-key])))
      (is (= "bgm-key" (get-in (first (:records dat)) [:render-plan :bgm-blob-key])))
      (is (= 1 (count (:ledger mem))))
      (is (= 1 (count (:ledger dat)))))))

(deftest datomic-store-nil-lookup-and-empty-filter
  (testing "unregistered production lookup is nil, records-of on an unknown production is empty"
    (let [dat (store/datomic-store)]
      (is (nil? (store/production dat "no-such")))
      (is (empty? (store/records-of dat "no-such"))))))
