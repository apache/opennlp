<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# OpenNLP DL

This module provides OpenNLP interface implementations for ONNX models using the `onnxruntime` dependency.

**Important**: This does not provide the ability to train models. Model training is done outside of OpenNLP. This code provides the ability to use ONNX models from OpenNLP.

Models used in the tests are available in the [opennlp evaluation test data](https://nightlies.apache.org/opennlp/opennlp-data.zip) location.

## NameFinderDL

`NameFinderDL` runs ONNX token-classification models that use BIO labels. Any
label in the form `B-<TYPE>` starts an entity and subsequent `I-<TYPE>` labels
continue that entity. The text after the prefix is reported as the OpenNLP span
type, for example `B-PER` and `I-PER` produce spans with type `PER`.

The finder uses BERT basic tokenization followed by WordPiece tokenization and
then maps the reconstructed WordPiece text back to the caller's original input
so returned spans can be used with `Span#getCoveredText(...)`. Span probabilities
are normalized from the model logits and are reported in the range `(0, 1]`.

Named entity models are commonly cased, so lower casing is disabled by default.
Set `InferenceOptions#setLowerCase(true)` only for models trained with uncased
input.

### Unicode text handling

Long input is split into overlapping chunks on the full Unicode `White_Space`
set (not Java's `\s`), so no-break space, ideographic space, and the other UCD
whitespace characters are recognized as delimiters. `NameFinderDL` locates
reconstructed entity text in the original input with a cursor-based matcher that
treats span spaces as flexible Unicode whitespace and compares other code points
case-insensitively, so `Span#getCoveredText(...)` works on text from PDFs, the
web, and multilingual sources.

Optional input folding is off by default and controlled through
`InferenceOptions`:

```java
InferenceOptions options = new InferenceOptions();
options.setNormalizeWhitespace(true);  // each Unicode whitespace -> ASCII space (offset-preserving)
options.setNormalizeDashes(true);      // Unicode dashes -> hyphen-minus (offset note below)
NameFinderDL finder = new NameFinderDL(model, vocab, ids2Labels, options, sentenceDetector);
```

Whitespace folding is length-preserving, so it never moves offsets. Dash folding can shrink a
non-BMP dash by one UTF-16 unit, but `NameFinderDL.findInOriginal` maps decoded spans back through
the normalization `Alignment`, so reported spans stay correct in the original input even for
non-BMP dashes. (`NameFinderDL.find` returns normalized-text offsets, which differ from the
original only in that non-BMP-dash case.)

The same options apply to `DocumentCategorizerDL`. The underlying
`CharClass` / `CodePointSet` engine and the broader normalization pipeline live
in `opennlp.tools.util.normalizer` and are documented in the OpenNLP manual
chapter *Text Normalization*.

Export a Hugging Face NER model to ONNX, e.g.:

```bash
python -m transformers.onnx --model=dslim/bert-base-NER --feature token-classification exported
```

## DocumentCategorizerDL

Uses the same Unicode whitespace chunking and optional `InferenceOptions`
normalization as `NameFinderDL` (see above).

Export a Huggingface classification (e.g. sentiment) model to ONNX, e.g.:

```bash
python -m transformers.onnx --model=nlptown/bert-base-multilingual-uncased-sentiment --feature sequence-classification exported
```

## Input validation and overlapping spans

- `NameFinderDL.find(...)` reports spans in the coordinates of the joined input it ran inference on,
  which differ from the original text only when length-changing dash folding is enabled.
  `NameFinderDL.findInOriginal(...)` reports original-text coordinates; code that holds a
  `TokenNameFinder` can check for `OffsetMappingNameFinder` to reach it.
- Spans that overlap where consecutive chunks overlap are resolved longest-wins, ties going to the
  higher probability.
- Whitespace-only input yields no spans without running the model.
- `NameFinderDL` rejects a `null` input array or a `null` token, and `DocumentCategorizerDL`
  rejects a `null` or empty input array, a `null` document, and a document with no non-whitespace
  token, with `IllegalArgumentException`.

## SentenceVectors

Convert a sentence vectors model to ONNX, e.g.:

Install dependencies:

```bash
python3 -m pip install optimum onnx onnxruntime
```

Convert the model:

```python
from optimum.onnxruntime import ORTModelForFeatureExtraction
from transformers import AutoTokenizer
from pathlib import Path


model_id="sentence-transformers/all-MiniLM-L6-v2"
onnx_path = Path("onnx")

# load vanilla transformers and convert to onnx
model = ORTModelForFeatureExtraction.from_pretrained(model_id, from_transformers=True)
tokenizer = AutoTokenizer.from_pretrained(model_id)

# save onnx checkpoint and tokenizer
model.save_pretrained(onnx_path)
tokenizer.save_pretrained(onnx_path)
```
