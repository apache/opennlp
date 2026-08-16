# Legal corpus licensing record

Verified live on 2026-08-16. Re-verify before any redistribution beyond local
research use. Raw downloads and normalized outputs live outside the repository
(default `~/.cache/opennlp-legal-corpus`) and are never committed; the
repository carries only these scripts, the snapshot pins, and the authored
fixtures under `fixtures/`.

| Source | What | License basis | Fetch route |
| --- | --- | --- | --- |
| Bouvier's Law Dictionary, 6th revised edition (1856) | Hand-keyed HTML transcription, 26 per-letter files (primary dictionary: headwords + definitions) | Public domain: published 1856, author John Bouvier died 1851; the transcription carried no separate claim and a keyed reproduction of a public-domain text adds no new copyright in the US | Pinned Wayback Machine snapshots (2001/2002) of the former constitution.org hosting; pins in `bouvier-snapshots.tsv` |
| Black's Law Dictionary, 2nd edition (1910) | OCR text (optional, headword cross-checks only; OCR too noisy for definitions) | Public domain: US publication before 1928 | Internet Archive item `lawdictionar_blac_1910_00` |
| Caselaw Access Project, United States Reports volumes | Per-case JSON with full opinion text | Court opinions are edicts of government, public domain; CAP's remaining access restrictions were lifted in 2024. Volumes are capped at 275 (through 1927) so volume front matter and reporter headnotes are also public domain by age | `https://static.case.law/us/<N>.zip` |

Notes recorded at acquisition time:

- The classic `bouvier.txt` at constitution.org is gone (site relayout, 404
  verified 2026-08-16), which is why the fetch goes through pinned Wayback
  snapshots of the per-letter HTML instead. The pin also makes every fetch
  byte-identical and hashable.
- The plan originally listed Black's 2nd as the primary dictionary and Bouvier
  as backup. Reversed 2026-08-16: no clean machine-readable Black's 2nd
  exists, only rough OCR, while the Bouvier transcription is hand-keyed and
  clean. Black's remains available behind `WITH_BLACKS=1` for headword
  cross-checks.
- Deliberately avoided: current editions of Black's (proprietary), Wex
  (CC BY-SA, share-alike), and post-1927 West editorial matter (headnotes,
  key numbers).
- Every download is recorded in `$LEGAL_CORPUS_HOME/MANIFEST.tsv` as
  url, UTC fetch date, sha256, and size in bytes.
