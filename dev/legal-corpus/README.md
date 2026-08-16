# Legal corpus acquisition

These scripts download checksum-pinned legal data and normalize it into the
dictionary and passage files used by the embedding evaluation. Source terms
are listed in `LICENSING.md`.

Data is stored under `$LEGAL_CORPUS_HOME`, which defaults to
`~/.cache/opennlp-legal-corpus`.

## Usage

```
export LEGAL_CORPUS_HOME="$HOME/.cache/opennlp-legal-corpus"
./fetch-dictionary.sh              # Bouvier 1856, 26 pinned Wayback files
WITH_BLACKS=1 ./fetch-dictionary.sh  # optional Black's 2nd edition OCR
./fetch-reporter.sh 190 220        # pinned CAP U.S. Reports volumes
./fetch-scripts-test.sh             # offline checksum tests
```

The remaining commands use the `opennlp-embeddings` CLI from the binary
distribution:

```
CORPUS_DIR="$LEGAL_CORPUS_HOME"
bin/embeddings NormalizeDictionary \
  -rawDir "$CORPUS_DIR/raw/bouvier" \
  -out "$CORPUS_DIR/normalized/dictionary.tsv"
bin/embeddings NormalizeReporter \
  -rawDir "$CORPUS_DIR/raw/us" \
  -out "$CORPUS_DIR/normalized/passages.jsonl"
bin/embeddings LearnVocabulary \
  -dictionary "$CORPUS_DIR/normalized/dictionary.tsv" \
  -passages "$CORPUS_DIR/normalized/passages.jsonl" \
  -out "$CORPUS_DIR/normalized/vocabulary.tsv"
bin/embeddings DistillModel \
  -teacher sentence-transformers/all-MiniLM-L6-v2 \
  -out "$CORPUS_DIR/model" \
  -terms "$CORPUS_DIR/normalized/vocabulary.tsv"
bin/embeddings EvalVectorSearch \
  -model "$CORPUS_DIR/model" \
  -passages "$CORPUS_DIR/normalized/passages.jsonl" \
  -dictionary "$CORPUS_DIR/normalized/dictionary.tsv" \
  -out "$CORPUS_DIR/report.md"
```

`EvalVectorSearch` builds the exact and quantized indexes and writes markdown
and TSV reports with fidelity, definition-to-headword retrieval, half-passage
retrieval, throughput, and storage measurements.

A Lucene HNSW baseline runs the same measurements against a graph index. It is
in the test tree, so Lucene remains a test-scope dependency. Run it from the
repository root:

```
./mvnw -pl opennlp-extensions/opennlp-embeddings -am \
  -Dtest=HnswBaselineRunnerTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dopennlp.forkCount=1 \
  -Dopennlp.hnsw.model="$CORPUS_DIR/model" \
  -Dopennlp.hnsw.passages="$CORPUS_DIR/normalized/passages.jsonl" \
  -Dopennlp.hnsw.dictionary="$CORPUS_DIR/normalized/dictionary.tsv" \
  -Dopennlp.hnsw.output="$CORPUS_DIR/hnsw-report.md" \
  -Dopennlp.hnsw.topK=10 test
```

The report's per-vector footprint is serialized Lucene vector and graph
storage, not live JVM memory.

The fetch scripts verify new and cached files against the checked-in SHA-256
values and sizes. Successful downloads are recorded in
`$LEGAL_CORPUS_HOME/MANIFEST.tsv`. A failed download can be retried safely.

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

`fixtures/mini-dictionary.tsv` and `fixtures/mini-passages.jsonl` are
self-authored examples in these formats. They do not contain fetched text.

## Reference corpus

- Bouvier: 6,270 entries from 26 letter files.
- United States Reports volumes 190 through 220: 22,087 passages.

Run both evaluation commands to produce model-specific quality, storage, and
throughput results for the current JVM and hardware.
