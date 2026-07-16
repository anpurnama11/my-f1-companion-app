# Lode map — F1app

Hierarchical index of all lode files. Update when files are added/removed.

```
lode/
  summary.md                         # living one-paragraph snapshot
  terminology.md                     # domain + project glossary
  practices.md                       # patterns, conventions, invariants
  lode-map.md                        # this file
  architecture/
    architecture.md                  # module, DI, layers, tech choices (ticket 01)
  design-system/
    theme.md                         # dark-only M3 theme + Circuits/Tyres/Shapes/Spacing (ticket 02)
  wayfinder/
    f1app/
      map.md                         # destination spec + scope + fog patches
      tickets/
        01-architecture-and-modules.md   # CLOSED
        02-design-system-theme.md        # CLOSED
        03-data-layer-and-refresh.md     # open (blocked_by 01, 04)
        04-api-client-and-enrichment-scope.md  # open
        05-navigation-and-deep-links.md  # open (blocked_by 01)
        06-widget-technology.md          # open (blocked_by 01)
        07-countdown-widget-specifics.md # open (blocked_by 02, 03, 06)
  tmp/                               # git-ignored session scraps
```

Related-but-outside: `lode/wayfinder/f1app/map.md` is the destination spec (the
"where we're going" doc). The lode files above describe the *current state* of the
system as it gets built.
