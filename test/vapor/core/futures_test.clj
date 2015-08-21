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
    (succeed! p "result")
    (is (realized? p))
    (is (= @p "result"))))

(deftest test-transform
  (let [p1 (settable-future)
        p2 (>| p1 #(Integer/parseInt %))]
    (succeed! p1 "42")
    (is (realized? p2))
    (is (= 42 @p2))))

(deftest test-async-transform
  (let [p1 (settable-future)
        p2 (settable-future)
        r1 (atom nil)
        p3 (>>| p1 (fn [r] (swap! r1 (fn [_] r))
                     p2))]
    (is (not (realized? p1)))
    (is (not (realized? p2)))
    (is (not (realized? p3)))
    (succeed! p1 42)
    (is (realized? p1))
    (is (not (realized? p2)))
    (is (not (realized? p3)))
    (is (= 42 @r1))
    (succeed! p2 "foo")
    (is (realized? p2))
    (is (realized? p3))
    (is (= "foo" @p3))))

(deftest test-callbacks
  (let [p (settable-future)
        res (atom nil)
        p2 (then! p (fn [r] (swap! res (fn [_] (:result r)))))]
    (succeed! p 42)
    (is (= 42 @res))
    (is (= p p2))))