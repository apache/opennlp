# Legal corpus acquisition

These tools fetch open legal data, verify and record it, and normalize it into
two small interchange files consumed by the embedding evaluation. Licensing
details and acquisition-time findings are in `LICENSING.md`.

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
CLI DistillModel        -teacher sentence-transformers/all-MiniLM-L6-v2 \
                        -out $H/model -terms $H/normalized/vocabulary.tsv
CLI EvalVectorSearch    -model $H/model \
                        -passages $H/normalized/passages.jsonl \
                        -dictionary $H/normalized/dictionary.tsv \
                        -out $H/report.md
```

The last command is the whole evaluation loop: it builds the exact and the
quantized index over the embedded passages and writes one markdown report
(and a TSV twin) with index fidelity, definition-to-headword retrieval,
half-passage retrieval, throughput, and storage cost.

A Lucene HNSW baseline reruns the same measurements against a graph index
for comparison. It lives in the module's test tree so Lucene stays a
test-scope dependency. From the repository root, run it through the Maven
test runner:

```
./mvnw -pl opennlp-extensions/opennlp-embeddings -am \
  -Dtest=HnswBaselineRunnerTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dopennlp.forkCount=1 \
  -Dopennlp.hnsw.model="$H/model" \
  -Dopennlp.hnsw.passages="$H/normalized/passages.jsonl" \
  -Dopennlp.hnsw.dictionary="$H/normalized/dictionary.tsv" \
  -Dopennlp.hnsw.output="$H/hnsw-report.md" \
  -Dopennlp.hnsw.topK=10 test
```

The report's per-vector footprint is serialized Lucene vector and graph
storage, not live JVM memory.

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

The full evaluation used 22,087 passages and 6,270 headwords at dimension
256 and top 10. On the author's workstation, the default JVM run produced:

| index | storage bytes/vector | build (ms) | QPS (1 thread) |
|---|---|---|---|
| exact | 1024.000 | 16 | 842 |
| Lucene HNSW | 1049.958 | 8466 | 4212 |

HNSW reached 0.976 recall at 10 against the exact scan and 0.921 rank-1
agreement. Its definition-to-headword MRR at 10 was 0.069, and its
half-passage MRR at 10 was 0.804. Timings are environment-sensitive. The
exact and TurboQuant figures count their stored row payloads; the HNSW
figure counts Lucene's serialized vector data, metadata, and graph files.
