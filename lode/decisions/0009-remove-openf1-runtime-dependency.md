# 0009 — Remove the OpenF1 runtime dependency

**Status: accepted**

OpenF1 is removed from the app's runtime source graph because its availability
now depends on a paid tier. `f1api.dev` supplies schedule/session times,
Jolpica supplies results, pit-stop durations, and circuit history, and F1DB
artwork is bundled during the build. Top speed is removed from v1 because the
remaining runtime APIs do not provide an equivalent measurement; a future
build-time FastF1 import is acceptable but not an Android runtime dependency.
This keeps the app offline-capable for artwork and avoids replacing OpenF1 with
another mandatory runtime image service. See
[OpenF1 removal implementation history](https://github.com/anpurnama11/my-f1-companion-app/issues/17).
