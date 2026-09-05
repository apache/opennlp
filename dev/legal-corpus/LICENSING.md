# Legal corpus sources

The scripts store downloaded and normalized data outside the repository under
`$LEGAL_CORPUS_HOME`. Recheck the source terms before redistributing that data.

| Source | Use | Terms | Acquisition |
| --- | --- | --- | --- |
| Bouvier's Law Dictionary, 6th revised edition (1856) | Headwords and definitions | The 1856 US publication is in the public domain under the [US Copyright Office guidance](https://copyright.gov/what-is-copyright/). | The Wayback Machine timestamps, SHA-256 values, and sizes are in `bouvier-snapshots.tsv`. |
| Caselaw Access Project, United States Reports volumes 190 through 220 | Opinion passages | Harvard marks the Caselaw Data as [CC0 1.0](https://case.law/terms/). | The archive SHA-256 values and sizes are in `reporter-snapshots.tsv`. |
| Black's Law Dictionary, 2nd edition (1910) | Optional OCR for manual headword checks | The 1910 US publication is in the public domain under the Copyright Office guidance above. | Internet Archive item `lawdictionar_blac_1910_00`; this optional file is recorded in the local manifest but is not checksum-pinned here. |

Each successful download adds its URL, UTC fetch time, SHA-256 value, and size
to `$LEGAL_CORPUS_HOME/MANIFEST.tsv`. The repository contains the scripts,
checksum tables, and self-authored fixtures under `fixtures/`.
