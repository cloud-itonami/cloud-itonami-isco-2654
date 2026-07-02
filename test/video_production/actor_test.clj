(ns video-production.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [video-production.actor :as actor]
            [video-production.store :as store]))

(defn- fresh-store []
  (-> (store/mem-store)
      (store/register-production! {:production/id "prod-1"
                                   :subjects-consented? false
                                   :location-cleared? false})
      (store/register-production! {:production/id "prod-cleared"
                                   :subjects-consented? true
                                   :location-cleared? true})))

(def timeline
  {:resolution "720p"
   :fps 30
   :scenes [{:index 0} {:index 1}]
   :assets [{:kind "scene" :blobKey "frame-0" :meta {:sceneIndex 0}}
            {:kind "scene" :blobKey "frame-1" :meta {:sceneIndex 1}}
            {:kind "bgm" :blobKey "bgm-key"}]
   :lines [{:sceneIndex 0 :lineIndex 0 :voiceBlobKey "v-0-0" :text "hi"}]})

(deftest commits-a-clean-assemble-with-render-plan
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:production-id "prod-1" :op :assemble
                 :timeline timeline :stake :low}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (let [record (get-in result [:state :record])]
      (is (some? record))
      (testing "the committed record carries the douga render plan"
        (is (= 2 (count (get-in record [:render-plan :segments]))))
        (is (= "bgm-key" (get-in record [:render-plan :bgm-blob-key])))
        (is (= 1280 (get-in record [:render-plan :width])))))
    (is (= 1 (count (store/records-of st "prod-1"))))))

(deftest holds-on-unregistered-production
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:production-id "no-such" :op :assemble
                 :timeline timeline :stake :low}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (= :hold (:disposition (:state result))))))

(deftest interrupts-publish-until-clearance-approved
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:production-id "prod-1" :op :publish :stake :medium}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "prod-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= :publish (:op (get-in resumed [:state :record]))))
      (is (= 1 (count (store/records-of st "prod-1")))))))
