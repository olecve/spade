(ns spade.container)

(defprotocol IStyleContainer
  "A place where Spade mounts the CSS of its styles."
  (mounted-info
    [this style-name]
    "Returns the `info` mounted with `style-name`, or nil if that style is not mounted.")
  (mount-style!
    [this style-name css info]
    "Makes the style named `style-name` with the given `css` available.

    Keep `info` in memory, so that [[mounted-info]] can return it quickly."))
