(ns spade.react
  (:require
   [react :as react]
   [spade.container :refer [IStyleContainer]]
   [spade.container.alternate :refer [->AlternateStyleContainer]]
   [spade.container.dom :as dom]
   [spade.runtime :refer [*style-container*]]))

(defonce context (react/createContext nil))
(defonce Provider (.-Provider context))

(defn- provided-container []
  ;; NOTE: This hack is inspired by ReactN:
  ;; https://charles-stover.medium.com/how-reactn-hacks-react-context-9d112397f003
  (or (.-_currentValue2 context)
      (.-_currentValue context)))

;; NOTE: As soon as this namespace is used, we "upgrade" the global style-container
;; to also check the react context
(set! *style-container*
      (->AlternateStyleContainer
       provided-container
       *style-container*))

(defn with-style-container
  "A reagent component that renders the styles of its children into `container`.

  Wrap the part of your app whose styles must go to another place, often the
  root of the app:

    [with-style-container container
     [your-view]]

  Use one container per render. If `container` changes between renders, the
  behavior is undefined."
  [^IStyleContainer container & children]
  (into [:> Provider {:value container}]
        children))

(defn with-dom
  "A reagent component that renders the styles of its children into another DOM element.

  The first argument is the element, or a function that returns it. The
  component uses [[with-style-container]]."
  [get-dom-target & _children]
  (let [container (dom/create-container get-dom-target)]
    (fn with-dom-render [_ & children]
      (into [with-style-container container]
            children))))
