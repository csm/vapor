(ns vapor.core.futures-test
  (:import (com.google.common.util.concurrent Futures SettableFuture))
  (:require [clojure.test :refer :all]
            [vapor.core.futures :refer :all]))

(deftest basic-completed-future
  (let [f (Futures/immediateFuture "foo")
        lf (listenable-future f)]
    (is (realized? lf))
    (is (= "foo" (deref lf)))))

(deftest basic-promise
  (let [p (settable-future)]
    (is (not (realized? p)))
    (succeed p "result")
    (is (realized? p))
    (is (= @p "result"))))

(deftest test-transform
  (let [p1 (settable-future)
        p2 (>| p1 #(Integer/parseInt %))]
    (succeed p1 "42")
    (is (realized? p2))
    (is (= 42 @p2))))
