(ns spade.jvm-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [spade.collision.a :as a]
   [spade.collision.b :as b]
   [spade.container.atom :as atom-container]
   [spade.core :refer [defclass with-styles-container]]))

(defclass blue-class []
  {:color "blue"})

(deftest with-styles-container-test
  (testing "Render styles to dynamically-provided Atom container"
    (let [styles (atom nil)
          container (atom-container/create-container styles)
          style-name (with-styles-container container
                       (blue-class))]
      (is (= "blue-class" style-name))
      (is (= ".blue-class{color:blue}"
             (get @styles style-name))))))

(deftest same-style-name-test
  (testing "Styles with the same name in different namespaces get different classes"
    (let [styles (atom nil)
          container (atom-container/create-container styles)
          [a-name b-name] (with-styles-container container
                            [(a/same-name) (b/same-name)])]
      (is (not= a-name b-name))
      (is (= (str "." a-name "{color:red}")
             (get @styles a-name)))
      (is (= (str "." b-name "{color:navy}")
             (get @styles b-name))))))
