# Changes from upstream

This branch is [spade 2.0.0](https://github.com/dhleong/spade/tree/2.0.0) plus
the changes below. Each change is a separate pull request in this fork, so it
can be ported to [dhleong/spade](https://github.com/dhleong/spade) on its own.

## Unreleased

### Changed

- Published to Clojars as `org.clojars.olecve/spade` instead of
  `net.dhleong/spade`. ([#6](https://github.com/olecve/spade/pull/6), fork
  only)

### Fixed

- Two style factories with the same name no longer share one class name.
  Under `:advanced` optimizations, names local to a closure are minified per
  scope, so two factories could both get the class `a` and one style
  overwrote the other. On the JVM, styles with the same name in different
  namespaces collided the same way. The second factory now gets a generated
  suffix, for example `a-SPD1234`.
  ([#3](https://github.com/olecve/spade/pull/3), not sent upstream yet)
