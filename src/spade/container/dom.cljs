(ns spade.container.dom
  "A style container that renders styles into `<style>` elements in the DOM.

  It keeps references to those elements in the `styles` atom, or in
  `*injected-styles*` when there is no `styles` atom. It adds the elements to
  `target-dom`, or to `document.head` when there is no `target-dom`."
  (:require
   [spade.container :refer [IStyleContainer]]))

(defonce ^:dynamic *injected-styles* (atom nil))

(defn- perform-update! [obj css]
  (set! (.-innerHTML (:element obj)) css))

(defn update! [styles-container id css info]
  (swap! styles-container update id
         (fn update-injected-style [obj]
           (when-not (= (:source obj) css)
             (perform-update! obj css))
           (assoc obj :source css :info info))))

(defn inject! [target-dom styles-container id css info]
  (let [element (doto (js/document.createElement "style")
                  (.setAttribute "spade-id" (str id)))
        obj {:element element
             :source css
             :info info
             :id id}]
    (assert (some? target-dom)
            "An <head> element or target DOM is required to inject the style.")

    (.appendChild target-dom element)

    (swap! styles-container assoc id obj)
    (perform-update! obj css)))

(deftype DomStyleContainer [target-dom styles]
  IStyleContainer
  (mounted-info [_ style-name]
    (let [resolved-container (or styles
                                 *injected-styles*)]
      (:info (get @resolved-container style-name))))

  (mount-style! [_ style-name css info]
    (let [resolved-container (or styles
                                 *injected-styles*)]
      (if (contains? @resolved-container style-name)
        (update! resolved-container style-name css info)

        (let [resolved-dom (or (when (ifn? target-dom)
                                 (target-dom))
                               target-dom
                               (.-head js/document))]
          (inject! resolved-dom resolved-container style-name css info))))))

(defn create-container
  "Creates a DomStyleContainer.

  With no arguments, it renders into `document.head` and keeps the style
  references in `*injected-styles*`. To render into another element, for
  example in a Shadow DOM, pass `target-dom`. It can be the element or a
  function that returns the element. The container then keeps the style
  references in a new atom.

  To keep the style references in your own atom, pass it as the second
  argument."
  ([] (create-container nil))
  ([target-dom] (create-container target-dom (when target-dom
                                               (atom nil))))
  ([target-dom styles-container]
   (->DomStyleContainer target-dom styles-container)))
