(ns spade.container)

(defprotocol IStyleContainer
  "Anything Spade can 'mount' styles into for its style components."
  (mounted-info
    [this style-name]
    "Returns the info object passed when `style-name` was mounted.

    Returns nil if that style is not currently mounted.")
  (mount-style!
    [this style-name css info]
    "Ensures the style with the given name and CSS is available.

    `info` should be stored in memory, so a call to [[mounted-info]] can
    retrieve it quickly."))
