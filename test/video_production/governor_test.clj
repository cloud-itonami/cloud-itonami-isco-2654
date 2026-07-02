(ns video-production.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [video-production.governor :as governor]
            [video-production.store :as store]))

(defn- fresh-store []
  (-> (store/mem-store)
      (store/register-production! {:production/id "prod-1"
                                   :subjects-consented? true
                                   :location-cleared? true})
      (store/register-production! {:production/id "prod-uncleared"
                                   :subjects-consented? false
                                   :location-cleared? true})))

(def timeline
  {:scenes [{:index 0}]
   :assets [{:kind "scene" :blobKey "frame-0" :meta {:sceneIndex 0}}]
   :lines [{:sceneIndex 0 :lineIndex 0 :voiceBlobKey "v-0-0" :text "hi"}]})

(defn- proposal [op] {:op op :effect :propose :stake :low :confidence 0.95})

(deftest ok-on-clean-assemble
  (let [v (governor/check {:production-id "prod-1" :timeline timeline}
                          {} (proposal :assemble) (fresh-store))]
    (is (:ok? v))
    (is (not (:escalate? v)))))

(deftest hard-holds
  (testing "unregistered production"
    (let [v (governor/check {:production-id "no-such" :timeline timeline}
                            {} (proposal :assemble) (fresh-store))]
      (is (:hard? v))
      (is (some #(= :no-production (:rule %)) (:violations v)))))
  (testing "non-propose effect"
    (let [v (governor/check {:production-id "prod-1" :timeline timeline}
                            {} (assoc (proposal :assemble) :effect :write!) (fresh-store))]
      (is (:hard? v)))))

(deftest escalations
  (testing "publish without full consent/rights clearance"
    (let [v (governor/check {:production-id "prod-uncleared"}
                            {} (proposal :publish) (fresh-store))]
      (is (not (:hard? v)))
      (is (:escalate? v))
      (is (some #(= :rights-clearance (:rule %)) (:escalations v)))))
  (testing "publish with clearance is ok"
    (let [v (governor/check {:production-id "prod-1"}
                            {} (proposal :publish) (fresh-store))]
      (is (:ok? v))))
  (testing "assemble with an empty douga render plan"
    (let [v (governor/check {:production-id "prod-1" :timeline {:scenes []}}
                            {} (proposal :assemble) (fresh-store))]
      (is (not (:hard? v)))
      (is (:escalate? v))
      (is (some #(= :empty-render-plan (:rule %)) (:escalations v)))))
  (testing "low confidence"
    (let [v (governor/check {:production-id "prod-1" :timeline timeline}
                            {} (assoc (proposal :assemble) :confidence 0.2) (fresh-store))]
      (is (:escalate? v)))))
