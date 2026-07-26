# Third-party notices

## F1DB circuit artwork

Circuit layout artwork in `app/src/main/res/drawable-nodpi/circuit_*.webp`
was imported from [F1DB](https://github.com/f1db/f1db), revision
`v2026.0.1`, from its `src/assets/circuits/white-outline` collection.

F1DB is licensed under CC BY 4.0. Attribution is retained here; the pinned
revision and source filename mapping are in
`tools/f1db/revision.txt` and `tools/f1db/circuit-artwork-map.json`.

To reproduce the checked-in resources from that pin, run:

```bash
python3 tools/f1db/import-circuit-artwork.py
```

## Wikipedia REST summary

The "About" biography text on the redesigned DriverDetail and
TeamDetail screens is fetched from the [Wikipedia REST API
summary endpoint](https://en.wikipedia.org/api/rest_v1/page/summary/{title})
at runtime via
`app/src/main/java/com/anpurnama/f1_app/f1/data/WikipediaApi.kt`
(constants: `WIKIPEDIA_REST_BASE`, `WIKIPEDIA_USER_AGENT`; DTO:
`WikipediaSummary`; extension: `HttpClient.getWikipediaSummary`).

The summary text (`extract`) is reused under [CC BY-SA
4.0](https://creativecommons.org/licenses/by-sa/4.0/). Attribution
("From Wikipedia, the free encyclopedia, under CC BY-SA 4.0" +
link to the article URL) is rendered in the UI by the screen
layer; the article URL is the `contentUrl` field on the
`WikipediaSummary` DTO.

The Wikimedia edge requires a project-identifying `User-Agent`
header — the constant `WIKIPEDIA_USER_AGENT` carries
`F1app/1.0 (https://github.com/anpurnama/F1app)`. The contact URL
in that constant is the only project-side identifier sent to
Wikipedia; no API key, no auth, no telemetry.

To reproduce the same response shape, fetch directly:

```bash
curl -H "User-Agent: F1app/1.0 (https://github.com/anpurnama/F1app)" \
  "https://en.wikipedia.org/api/rest_v1/page/summary/Andrea_Kimi_Antonelli"
```
