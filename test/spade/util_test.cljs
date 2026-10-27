(ns spade.util-test
  (:require
   [cljs.test :refer-macros [deftest testing is]]
   [spade.core :as spade]
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

  (testing "Keep the name of the first factory when names are the same"
    ; simulate two fns that end up with the same minified
    ; name, as can happen in a very large advanced build
    (let [first-name (factory->name #js {:name "sameName"})
          second-name (factory->name #js {:name "sameName"})]
      (is (= "sameName" first-name))
      (is (not= first-name second-name)))))

(defn- with-class-prefix
  "Calls `f` while `prefix` is the class prefix, then removes the prefix."
  [prefix f]
  (spade/configure! {:class-prefix prefix})
  (try
    (f)
    (finally
      (spade/configure! {:class-prefix ""}))))

(deftest class-prefix-test
  (testing "Start every name with the class prefix"
    (with-class-prefix "ui_"
      #(is (= "ui_prefixed-style"
              (factory->name #js {:name "prefixed_style"})))))

  (testing "Start a generated name with the class prefix too"
    (with-class-prefix "ui_"
      #(is (re-matches #"ui_SPD\d+"
                       (factory->name #js {:name ""})))))

  (testing "Reject a prefix that can't start a CSS class name"
    (is (thrown-with-msg? js/Error #"class prefix"
                          (spade/configure! {:class-prefix "1ui"})))))

(deftest configure!-test
  (testing "Reject an option Spade doesn't know"
    (is (thrown-with-msg? js/Error #"Unknown option"
                          (spade/configure! {:class-prefx "ui_"})))))
