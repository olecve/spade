(ns spade.core
  #?(:cljs
     (:require-macros
      [net.cgrand.macrovich :as macros]))
  (:require
   #?@(:clj [[net.cgrand.macrovich :as macros]
             [spade.runtime]])
   [clojure.string :as str]
   [clojure.walk :refer [postwalk prewalk]]
   [spade.util :refer [factory->name build-style-name]]))

(defn- extract-key [style]
  (-> style first meta :key))

(defn- find-key-meta [style]
  (->> style

       (postwalk
        (fn [form]
          (if (and (map? form)
                   (::key form))
            form

            (if-let [k (:key (meta form))]
              {::key k}

              (if-let [k (when (sequential? form)
                           (some ::key form))]
                {::key k}

                form)))))

       ::key))

(def ^:private auto-imported-at-form?
  #{'at-font-face
    'at-import
    'at-media
    'at-supports})

(defn- replace-at-forms [style]
  (postwalk
   (fn [element]
     (if (and (symbol? element)
              (auto-imported-at-form? element))
       (symbol "garden.stylesheet" (name element))

       element))
   style))

(defn- clean-property-name [n]
  (when n
    (str/replace n #"[^a-zA-Z0-9_-]" "-")))

(defn- css-var? [element]
  (and (keyword? element)
       (let [n (name element)]
         (and (str/starts-with? n "*")
              (str/ends-with? n "*")))))

(defn- varify-key [element]
  (let [space (namespace element)
        n (name element)]
    (keyword (str (when space "--")
                  (clean-property-name (namespace element))
                  "--"
                  (clean-property-name
                   (subs n 1 (dec (count n))))))))

(defn- varify-val [element]
  `(spade.runtime/->css-var ~(varify-key element)))

(defn- rename-vars [style]
  (prewalk
   (fn [element]
     (cond
       (map-entry? element)
       (let [var-key? (css-var? (key element))
             var-val? (css-var? (val element))]
         (if (or var-key? var-val?)
           (-> element
               (update 0 (if var-key? varify-key identity))
               (update 1 (if var-val? varify-val identity)))

           element))

       (css-var? element)
       (varify-val element)

       :else
       element))
   style))

(defn- with-composition [name-var key-var style-var]
  (let [composes-var (gensym "composes")
        css-style-var (gensym "css-style")
        base (cond-> {:css `(when ~name-var
                              (spade.runtime/compile-css ~css-style-var))
                      :name name-var}
               key-var (assoc ::key key-var))]
    `(let [[~composes-var ~css-style-var] (spade.runtime/extract-composes ~style-var)]
       (cond-> ~base
         ~composes-var (assoc :composes ~composes-var)))))

(defn- build-style-naming-let
  [style params name-var]
  (let [has-key-meta? (some? (find-key-meta style))
        static-key (extract-key style)
        key-var (gensym "key")]
    (cond
      ; easiest case: no params? no need to call build-style-name
      (nil? (seq params))
      [nil name-var nil nil]

      ; typical case: no custom key
      (not has-key-meta?)
      [nil name-var nil nil]

      ; okay case: a (nearly) static key that we can pull out and compute
      ; directly, without building the rest of the style form
      static-key
      [nil name-var key-var
       `[~key-var ~static-key]]

      ; fancy case: custom :key
      :else
      (let [base-style-var (gensym "base-style")]
        [base-style-var name-var key-var
         `[~base-style-var ~(vec style)
           ~key-var (:key (meta (first ~base-style-var)))]]))))

(defn- prefix-at-media [style]
  (postwalk
   (fn [form]
     (if (and (list? form)
              (= 'garden.stylesheet/at-media (first form)))
       (let [[sym media-map & body] form]
         `(~sym ~media-map
                [:& ~@body]))

       form))
   style))

(defn- transform-named-style [style params style-name-var]
  (let [style-var (gensym "style")
        style (->> style prefix-at-media rename-vars)
        [base-style-var name-var key-var style-naming-let] (build-style-naming-let
                                                            style params style-name-var)
        style-decl (if base-style-var
                     `(into [(str "." ~name-var)] ~base-style-var)
                     (into [`(str "." ~name-var)] style))]
    `(let ~(vec (concat style-naming-let
                        [style-var style-decl]))
       ~(with-composition name-var key-var style-var))))

(defn- transform-keyframes-style [style params style-name-var]
  (let [style (->> style prefix-at-media rename-vars)
        [base-style-var name-var key-var style-naming-let] (build-style-naming-let
                                                            style params style-name-var)
        info-map (cond-> `{:css (when ~name-var
                                  (spade.runtime/compile-css
                                   (garden.stylesheet/at-keyframes
                                    ~name-var
                                    ~(or base-style-var (vec style)))))
                           :name ~name-var}
                   key-var (assoc ::key key-var))]

    ; this (let) might get compiled out in advanced mode anyway, but
    ; let's just generate simpler code instead of having a redundant
    ; (let) if the keyframes take no params
    (if style-naming-let
      `(let ~style-naming-let ~info-map)
      info-map)))

(defn- transform-style [mode style params style-name-var]
  (let [style (replace-at-forms style)]
    (cond
      (#{:global} mode)
      `{:css (spade.runtime/compile-css ~(vec (rename-vars style)))}

      ; keyframes are a bit of a special case
      (#{:keyframes} mode)
      (transform-keyframes-style style params style-name-var)

      :else
      (transform-named-style style params style-name-var))))

(defn- generate-style-name-fn [factory-fn-name factory-name-var style params]
  (cond
    (empty? params)
    `(fn [_#] @~factory-name-var)

    ; Custom :key meta; we need to generate the form to extract that.
    ; TODO: Ideally we can invoke the factory but *skip* CSS compilation,
    ; but since this is memoized (and :key isn't much used anyway) this is
    ; probably not a big deal for now. Would be a nice optimization, however.
    (some? (find-key-meta style))
    `(clojure.core/memoize
      (fn [params#]
        (let [dry-run# (~factory-fn-name nil params#)]
          (#'build-style-name
           @~factory-name-var
           (::key dry-run#)
           params#))))

    :else
    `(clojure.core/memoize
      (fn [params#]
        (#'build-style-name @~factory-name-var nil params#)))))

(defmulti ^:private declare-style
  (fn [mode _class-name params _name-fn-name _factory-fn-name]
    (case mode
      :global :static
      (cond
        (some #{'&} params) :variadic
        (every? symbol? params) :default
        :else :destructured))))
(defmethod declare-style :static
  [mode class-name _ name-fn-name factory-fn-name]
  `(def ~class-name (spade.runtime/ensure-style!
                     ~mode
                     (meta (var ~class-name))
                     ~name-fn-name
                     ~factory-fn-name
                     nil)))
(defmethod declare-style :no-args
  [mode class-name _ name-fn-name factory-fn-name]
  `(defn ~class-name []
     (spade.runtime/ensure-style!
      ~mode
      (meta (var ~class-name))
      ~name-fn-name
      ~factory-fn-name
      nil)))
(defmethod declare-style :destructured
  [mode class-name params name-fn-name factory-fn-name]
  ; good case; since there's no variadic args, we can generate an :arglists
  ; meta and a simplified params list that we can forward simply
  (let [raw-params (->> (range (count params))
                        (map (fn [idx]
                               (gensym (str "param-" idx "-"))))
                        vec)]
    `(defn ~class-name
       {:arglists (quote ~(list params))}
       ~raw-params
       (spade.runtime/ensure-style!
        ~mode
        (meta (var ~class-name))
        ~name-fn-name
        ~factory-fn-name
        ~raw-params))))
(defmethod declare-style :variadic
  [mode class-name _params name-fn-name factory-fn-name]
  ; dumb case; with a variadic params vector, any :arglists we
  ; provide gets ignored, so we just simply collect them all
  ; and pass the list as-is
  `(defn ~class-name [& params#]
     (spade.runtime/ensure-style!
      ~mode
      (meta (var ~class-name))
      ~name-fn-name
      ~factory-fn-name
      params#)))
(defmethod declare-style :default
  [mode class-name params name-fn-name factory-fn-name]
  ; best case; simple params means we can use them directly
  `(defn ~class-name ~params
     (spade.runtime/ensure-style!
      ~mode
      (meta (var ~class-name))
      ~name-fn-name
      ~factory-fn-name
      ~params)))

(defn- declare-style-fns [mode class-name params style]
  {:pre [(symbol? class-name)
         (or (vector? params)
             (nil? params))]}
  (let [factory-fn-name (symbol (str (name class-name) "-factory$"))
        style-name-fn-name (symbol (str (name class-name) "-name$"))

        style-name-var (gensym "style-name")
        params-var (gensym "params")
        factory-name-var (gensym "factory-name")]
    `(do
       (defn ~factory-fn-name [~style-name-var ~params-var]
         ~(if params
            `(let [~params ~params-var]
               ~(transform-style mode style params style-name-var))
            (transform-style mode style nil style-name-var)))

       (let [~factory-name-var (delay
                                 (factory->name
                                  (macros/case
                                   :cljs ~factory-fn-name
                                   :clj (var ~factory-fn-name))))

             ~style-name-fn-name ~(generate-style-name-fn
                                   factory-fn-name factory-name-var style params)]

         ~(declare-style mode class-name params style-name-fn-name factory-fn-name)))))

(defn- with-docstring
  "Returns `[class-name params style]` from the arguments of a style macro.

  When the first argument after the name is a string, it is the docstring, and
  it goes on `class-name` as `:doc`."
  [class-name [first-arg & more :as args]]
  (if (string? first-arg)
    [(vary-meta class-name assoc :doc first-arg) (first more) (rest more)]
    [class-name (first args) (rest args)]))

#?(:clj
   (defn configure!
     "Sets options for the names Spade generates.

     Options:
     - `:class-prefix`, text that starts every class name and animation name. It
       must start with a letter or `_`, and contain only letters, digits, `_` and
       `-`. The default is no prefix.

     Call it before the first style renders: a style gets its name the first time
     it is used, and keeps it. Throws on an unknown option or an invalid value."
     [options]
     (spade.util/configure! options)))

(defmacro defclass
  "Defines a function named `class-name` that returns the CSS class name of a style.

  `params` are the parameters of the function, and the style can use them:

      (defclass ship-style [wing-color]
        {:background \"#999\"}
        [:.wing {:color wing-color}])

  The first map applies to the element that gets the class. The other forms
  apply to its descendants. In garden syntax, the example is:

      [:.ship-style {:background \"#999\"}
       [:.wing {:color wing-color}]]

  Use the class name in a reagent component:

      (defn ship [wing-color]
        [:div {:class (ship-style wing-color)}
         [:div.wing]])

  Like `defn`, it takes an optional docstring before `params`."
  {:arglists '([class-name params & style]
               [class-name docstring params & style])}
  [class-name & args]
  (let [[class-name params style] (with-docstring class-name args)]
    (declare-style-fns :class class-name params style)))

(defmacro defattrs
  "Defines a function named `class-name` that returns an attribute map for a style.

  It works like [[defclass]], but the function returns a map such as:

      {:class \"the-class-name\"}

  Use the map as the attributes of a reagent element:

      (defn ship [wing-color]
        [:div (ship-attrs wing-color)
         [:div.wing]])

  Like `defn`, it takes an optional docstring before `params`."
  {:arglists '([class-name params & style]
               [class-name docstring params & style])}
  [class-name & args]
  (let [[class-name params style] (with-docstring class-name args)]
    (declare-style-fns :attrs class-name params style)))

(defmacro defglobal
  "Defines a global style named `group-name`.

  Like [[defclass]] and [[defattrs]], it can contain more than one style. The
  styles apply globally, also to elements such as `<body>`. Spade adds the
  styles when the form is evaluated, so a global style cannot take parameters."
  [group-name & style]
  (declare-style-fns :global group-name nil style))

(defmacro defkeyframes
  "Defines a function named `keyframes-name` that returns the name of an `@keyframes` animation.

  Like [[defclass]], it can take parameters to generate the keyframes. Use the
  returned name in an `:animation` property:

      (defkeyframes anim-frames []
        [\"0%\" {:opacity 0}]
        [\"100%\" {:opacity 1}])

      (defclass serenity []
        {:animation [[(anim-frames) \"560ms\" 'ease-in-out]]})"
  [keyframes-name params & style]
  (declare-style-fns :keyframes keyframes-name params style))

(defmacro with-styles-container [container & body]
  (macros/case
   :cljs `(binding [spade.runtime/*style-container* ~container]
            ~@body)
   :clj `(with-bindings {#'spade.runtime/*style-container* ~container}
           ~@body)))
