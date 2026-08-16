# Legal corpus acquisition

Stage 0 of the legal vocabulary and vector index work (see
`turboquant-legal-search-plan.md` at the workspace root): fetch open legal
data, verify and record it, and normalize it into two small interchange files
the later stages consume. Licensing details and acquisition-time findings are
in `LICENSING.md`.

Everything lands under `$LEGAL_CORPUS_HOME` (default
`~/.cache/opennlp-legal-corpus`), never in the repository.

## Usage

```
./fetch-dictionary.sh              # Bouvier 1856, 26 pinned Wayback files
WITH_BLACKS=1 ./fetch-dictionary.sh  # additionally the Black's 2nd (1910) OCR
./fetch-reporter.sh 190 220        # CAP U.S. Reports volumes (default range)
```

Everything after the fetch is pure Java, via the opennlp-embeddings CLI
(`opennlp.embeddings.cmdline.CLI`; build the module, then put its classes and
dependencies on the classpath):

```
CLI NormalizeDictionary -rawDir $H/raw/bouvier -out $H/normalized/dictionary.tsv
CLI NormalizeReporter   -rawDir $H/raw/us      -out $H/normalized/passages.jsonl
CLI LearnVocabulary     -dictionary $H/normalized/dictionary.tsv \
                        -passages $H/normalized/passages.jsonl \
                        -out $H/normalized/vocabulary.tsv
```

The original Python normalizers were retired 2026-08-16 after the Java ports
reproduced their output byte for byte on the full first acquisition (see
`opennlp.embeddings.corpus`).

Fetches are idempotent (existing files are skipped) and every download is
appended to `$LEGAL_CORPUS_HOME/MANIFEST.tsv` with URL, UTC date, sha256, and
size. The Wayback Machine rate-limits; if a fetch dies mid-run, wait a moment
and rerun to resume.

## Interchange formats

`normalized/dictionary.tsv`, one entry per line:

```
HEADWORD<TAB>definition text, whitespace-collapsed
```

Headwords keep original casing and may be multi-word ("HABEAS CORPUS",
"A MENSA ET THORO"). One line per distinct headword, first occurrence wins.

`normalized/passages.jsonl`, one JSON object per line:

```
{"id": "<caseid>-<opinion>-<seq>", "case": "...", "cite": "200 U.S. 1",
 "date": "1906-01-02", "vol": "200", "text": "..."}
```

Passages pack whole paragraphs to about 1200 characters (hard cap 2400) so a
passage is a coherent run of argument.

## Fixtures

`fixtures/mini-dictionary.tsv` and `fixtures/mini-passages.jsonl` are tiny,
entirely self-authored files in exactly these formats, safe to copy into test
resources. They are NOT excerpts of the fetched data.

## Measured on first acquisition (2026-08-16)

- Bouvier: 6,270 entries from 26 letter files.
- One validation volume (us/200, 1906): 676 passages from 109 cases.
