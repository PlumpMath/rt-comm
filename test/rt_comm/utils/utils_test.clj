(ns rt-comm.utils.utils-test
  (:require [clojure.core.match :refer [match]]

            [clojure.test :as t :refer [is are run-tests deftest testing]]
            [taoensso.timbre :refer [debug info error spy]]

            [rt-comm.utils.utils :refer :all]))



(deftest add-to-col-in-table-test
  (let [vec-of-maps  [{:aa 1 :target-key [:a :c]} 
                      {:aa 2}] ;; note :target-key is missing here!
        items-to-add #{:d :c}]
    (is (= (add-to-col-in-table vec-of-maps :target-key items-to-add)
           [{:aa 1, :target-key #{:c :d :a}}   ;; added items here and set the coll type
            {:aa 2, :target-key #{:c :d}}])))) ;; created the whole key 


