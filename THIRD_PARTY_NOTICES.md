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
