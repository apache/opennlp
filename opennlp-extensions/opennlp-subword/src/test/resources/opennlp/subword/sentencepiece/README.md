<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License. You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# SentencePiece parity fixtures

Tiny trained `.model` files and matching `.fixtures.tsv` files used by
`SentencePieceParityTest` and related tests. They are **not** third-party
pretrained models: they are generated in-tree from `corpus.txt` plus a short
multilingual add-on list in `gen_fixtures.py`, using the reference
[sentencepiece](https://github.com/google/sentencepiece) Python package.

The expected outputs in the TSVs come from the reference implementation, not
from the Java code under test, so the parity tests stay independent of the
implementation they check.

## Regenerating the tiny models

From this directory (or any directory; pass absolute paths as needed):

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install sentencepiece
python gen_fixtures.py corpus.txt .
```

That trains each model listed in `MODELS` inside `gen_fixtures.py`
(`tiny-unigram`, `tiny-unigram-bytefb`, `tiny-bpe`, `tiny-unigram-identity`,
`tiny-unigram-suffix`) and writes:

- `<name>.model` — SentencePiece binary model
- `<name>.fixtures.tsv` — expected pieces, ids, UTF-16 spans, and normalized form
- `corpus-full.txt` — training corpus (`corpus.txt` plus multilingual lines)

Pin the `sentencepiece` package version you used if regenerating for a PR, so
reviewers can reproduce the same bytes.

## Validating the Java implementation

To verify parity end to end, regenerate the fixtures as above, then run the
test suite from the repository root:

```bash
./mvnw -pl opennlp-extensions/opennlp-subword -am test
```

`SentencePieceParityTest` asserts every fixture line piece for piece, span
for span, against `SentencePieceTokenizer`, for each bundled tiny model.

To additionally validate against real published models, generate fixtures for
a directory of pre-trained `*.model` files and point the eval test at it:

```bash
source .venv/bin/activate
python gen_real_fixtures.py /path/to/models
./mvnw -pl opennlp-extensions/opennlp-subword -am test \
    -Dopennlp.subword.eval.dir=/path/to/models
```

`SentencePieceRealModelEvalTest` is skipped unless
`opennlp.subword.eval.dir` is set.

## Real-model fixtures (optional, not bundled)

`gen_real_fixtures.py` writes the same TSV format for any directory of
pre-trained `*.model` files (no training). It reuses the escaping helpers and
input list from `gen_fixtures.py`:

```bash
source .venv/bin/activate   # same venv as above
python gen_real_fixtures.py /path/to/models
```

Real models and their TSVs are not checked into this tree; the script is for
local eval against published SentencePiece models.

## Fixture TSV format

Tab-separated, with backslash escapes (`\\`, `\t`, `\n`, `\r`):

```text
esc(input) TAB pieceCount TAB [esc(piece) TAB id TAB begin TAB end]... TAB esc(normalized)
```

`begin` / `end` are UTF-16 code-unit offsets into the original input (Java
`String` indexing).
