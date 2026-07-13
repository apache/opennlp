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

# Distilling a Model for OpenNLP Static Embeddings

This module loads static embedding tables; it does not produce them. A table is distilled once from a sentence-transformer teacher, offline, in Python, and then loaded in the JVM as many times as you like. This walks through distilling one and assembling the directory `StaticEmbeddingModel.load` expects, using a multilingual SentencePiece model (bge-m3) as the worked example.

The distillation tool is [Model2Vec](https://github.com/MinishLab/model2vec). It runs the teacher over its own vocabulary once, applies PCA and a Zipf weighting, and writes a flat per-token matrix. There is no training loop and no labelled data; a distillation is minutes on CPU, not hours on a GPU.

## 1. Set up the distiller

```bash
uv venv .venv-distill
uv pip install --python .venv-distill "model2vec[distill]"
```

## 2. Distill the teacher

bge-m3 is an XLM-RoBERTa/SentencePiece model with a 250k multilingual vocabulary, native dimension 1024.

```python
# distill_bge_m3.py
from model2vec.distill import distill

static = distill("BAAI/bge-m3", pca_dims=256)
static.save_pretrained("bge-m3-static")
print("dim:", static.dim)
```

```bash
.venv-distill/bin/python distill_bge_m3.py
```

This exact script ships in the module as `scripts/distill_bge_m3.py`, and `scripts/parity/` holds a harness that reruns the parity check and the single-thread speed comparison against the Python reference on any machine.

### On the dimension

`pca_dims` is the one quality knob worth thinking about, and bigger is not better. Distilling bge-m3 at 256 and at 512 gives the same cross-lingual similarity within noise (English/Chinese paraphrase around 0.69 either way), while 512 doubles the matrix on disk and in memory and cuts embedding throughput. PCA to 256 already captures the useful variance of the teacher; the extra dimensions are mostly noise that dilutes the signal. 256 is a good default, and it is where the reference potion tables sit too.

## 3. Assemble the model directory

`save_pretrained` writes `model.safetensors`, `tokenizer.json`, and `config.json`, but not the trained SentencePiece `.model` file. That file is what actually segments text, so copy it from the teacher's own repository (on the Hub it is `sentencepiece.bpe.model`) into the same directory:

```bash
cp bge-m3-tokenizer/sentencepiece.bpe.model bge-m3-static/
```

A loadable SentencePiece directory then holds:

```
bge-m3-static/
  sentencepiece.bpe.model   # copied from the teacher; segments the text
  tokenizer.json            # Unigram vocab; its row order maps to the matrix
  model.safetensors         # the embedding matrix (F16 here, read natively)
  config.json               # carries "normalize": true|false
```

`load` detects the SentencePiece layout from the `.model` file next to `tokenizer.json`; it does not need `tokenizer_config.json`, because the `.model` carries the model's own text normalizer. If you forget the `.model` file, the loader says so by name.

### Let the tool assemble it

Rather than assemble the directory by hand, run the `AssembleModel` command. It completes the directory in place and verifies it by loading it, so a run that prints a summary is a directory that works:

```
opennlp-embeddings AssembleModel -modelDir bge-m3-static
```

For a WordPiece distillation it derives the missing `vocab.txt` and `tokenizer_config.json` from `tokenizer.json` (the row order is the vocabulary in id order; the casing is the normalizer's lowercase flag). For a SentencePiece distillation it checks that the trained `.model` file is present and names the fix if it is not. Either way it prints the family, row count, and dimension of the loaded model.

## 4. Load and verify in the JVM

```java
StaticEmbeddingModel model = StaticEmbeddingModel.load(Path.of("bge-m3-static"));

// Multilingual: the same meaning across languages lands nearby.
double crossLingual = model.similarity(
    "The weather is beautiful today", "今天天气很好");   // high
double unrelated = model.similarity(
    "The weather is beautiful today", "quarterly earnings missed"); // low

// Sanity: neighbors of a word are its translations and case variants.
model.mostSimilar("coffee", 5);   // ▁coffee, ▁Coffee, ▁koffie, ▁kávé, ▁кофе
```

Confirm parity against the Python reference before trusting a fresh distillation: embed the same text on both sides and check the vectors match within floating-point tolerance. They should agree to a few parts in ten thousand, because the JVM path reproduces the reference tokenization and pooling exactly, not approximately.

## The WordPiece path

A WordPiece teacher (a BERT-family model such as bge-large-en) distills the same way. Its directory layout is the BERT one instead: `vocab.txt` (one token per line, line number is the row), `model.safetensors`, `config.json`, and `tokenizer_config.json` (whose `do_lower_case` sets the casing). `load` detects WordPiece from the presence of `vocab.txt`.

`save_pretrained` writes `tokenizer.json` rather than a `vocab.txt` for these, so derive `vocab.txt` from the `tokenizer.json` vocabulary in id order, and take `tokenizer_config.json` from the teacher for `do_lower_case`.

## Where a table's license comes from

Distillation carries the teacher's license onto the table. bge-m3 is MIT, so its distillation is freely redistributable; a table distilled from a non-commercial or share-alike teacher inherits those terms. Check the teacher before publishing a table.
