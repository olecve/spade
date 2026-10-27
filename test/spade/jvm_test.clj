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

(defclass red-class []
  {:color "red"})

(defn- shared-with-composes []
  {:composes [(red-class)]})

(defclass composes-from-call []
  (shared-with-composes)
  {:background "blue"})

(deftest composes-from-a-function-call-test
  (testing "Compose from a map that a function call returns"
    (let [styles (atom nil)
          container (atom-container/create-container styles)
          style-name (with-styles-container container
                       (composes-from-call))]
      (is (= "red-class composes-from-call" style-name))
      (is (= ".composes-from-call{background:blue}"
             (get @styles "composes-from-call"))))))

(defclass documented-class
  "Colors the text green."
  []
  {:color "green"})

(deftest docstring-test
  (testing "Keep the docstring of a defclass"
    (is (= "Colors the text green."
           (:doc (meta #'documented-class)))))

  (testing "Build the class of a defclass with a docstring"
    (let [styles (atom nil)
          container (atom-container/create-container styles)
          style-name (with-styles-container container
                       (documented-class))]
      (is (= ".documented-class{color:green}"
             (get @styles style-name))))))
