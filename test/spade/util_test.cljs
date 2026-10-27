(ns spade.util-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [spade.util :refer [factory->name]]))

(deftest factory->name-test
  (testing "Never prefix with illegal characters"
    ; simulate a fn under advanced compilation having
    ; a name that starts with _
    (is (= "_advanced-compile"
           (factory->name #js {:name "_advanced_compile"}))))

  (testing "Never prefix with illegal characters"
    ; simulate a fn under simple compilation not
    ; having *any* name
    (is (seq (factory->name #js {:name ""}))))

  (testing "Never return the same name for different factories"
    ; simulate two fns under advanced compilation whose
    ; minified names only differ by `$` and `_`
    (is (not= (factory->name #js {:name "$collide"})
              (factory->name #js {:name "_collide"})))))

