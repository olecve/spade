(ns hooks.styles
  "Hooks that tell clj-kondo what the functions of Spade's style macros return."
  (:require
   [clj-kondo.hooks-api :as api]))

(def ^:private auto-imported-at-rules
  '#{at-font-face at-import at-media at-supports})

(defn- at-rule-call? [node]
  (let [head (first (:children node))]
    (and (api/list-node? node)
         (api/token-node? head)
         (contains? auto-imported-at-rules (api/sexpr head)))))

(defn- lintable-style
  "Rewrites a style node into code clj-kondo can lint like the real macro's output.

  A call to an at-rule that Spade imports on its own becomes a `vector` call, so
  its arguments are linted without a require. Every node keeps its metadata, so a
  `:key` expression stays in use."
  [node]
  (cond
    (at-rule-call? node)
    (assoc node :children (->> (:children node)
                               rest
                               (map lintable-style)
                               (cons (api/token-node 'vector))))

    (:children node)
    (update node :children #(mapv lintable-style %))

    :else
    node))

(defn- style-call->defn
  "Rewrites a style macro call into a `defn` that returns `return-node`.

  A docstring after the name stays the docstring of the `defn`."
  [{:keys [node]} return-node]
  (let [[_ name-node & args] (:children node)
        [docstring-node params-node & style] (if (api/string-node? (first args))
                                               args
                                               (cons nil args))]
    {:node (with-meta
             (api/list-node
              (concat
               [(api/token-node 'defn) name-node]
               (when docstring-node
                 [docstring-node])
               [params-node
                (api/list-node
                 [(api/token-node 'let)
                  (api/vector-node [(api/token-node '_style)
                                    (->> style
                                         (mapv lintable-style)
                                         api/vector-node)])
                  return-node])]))
             (meta node))}))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn defclass
  "Lints `spade.core/defclass` as a function that returns a class name."
  [hook-input]
  (style-call->defn hook-input (api/string-node "")))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn defattrs
  "Lints `spade.core/defattrs` as a function that returns an attribute map."
  [hook-input]
  (style-call->defn hook-input (api/map-node [(api/keyword-node :class) (api/string-node "")])))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn defkeyframes
  "Lints `spade.core/defkeyframes` as a function that returns an animation name."
  [hook-input]
  (style-call->defn hook-input (api/string-node "")))
