(ns spade.core
  (:require-macros
   [spade.core])
  (:require
   [garden.core]
   [garden.stylesheet]
   [spade.runtime]
   [spade.util]))

(defn configure!
  "Sets options for the names Spade generates.

  Options:
  - `:class-prefix`, text that starts every class name and animation name. It
    must start with a letter or `_`, and contain only letters, digits, `_` and
    `-`. The default is no prefix.

  Call it before the first style renders: a style gets its name the first time
  it is used, and keeps it. Throws on an unknown option or an invalid value."
  [options]
  (spade.util/configure! options))
