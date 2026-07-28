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

This module loads static embedding tables and, with the `DistillModel` command, also produces them: a table is distilled once from a sentence-transformer teacher and then loaded in the JVM as many times as you like. The distiller replicates [Model2Vec](https://github.com/MinishLab/model2vec) in Java — it runs the teacher's ONNX graph over its own vocabulary once, applies principal component analysis (PCA) and a Zipf weighting (frequent tokens are down-weighted, after Zipf's law of word frequency), and writes a flat per-token matrix. There is no training loop and no labelled data; a distillation is minutes on CPU, not hours on a GPU.

## 1. Distill the teacher

```
opennlp-embeddings DistillModel -teacher BAAI/bge-m3 -out bge-m3-static -pcaDims 256
```

`-teacher` is a Hugging Face model id (its `tokenizer.json`, `tokenizer_config.json`, `onnx/model.onnx`, and, for an export that splits its weights out, `onnx/model.onnx_data` download once into `~/.cache/opennlp-embeddings/<org>-<model>`) or a local directory holding those files. `-pcaDims` defaults to 256. For a SentencePiece teacher like bge-m3 the trained `sentencepiece.bpe.model` is fetched alongside, because the static table keeps the teacher's segmentation. The command ends by completing the directory (the `AssembleModel` step) and verifying it by loading it, so a run that prints a summary is a directory that works.

Distil into a fresh directory. The command replaces the files it writes itself, but the assembly step never overwrites a `vocab.txt` or `tokenizer_config.json` an earlier run left behind, and a run that fails part way through leaves whatever it had written.

bge-m3 is an [XLM-RoBERTa](https://arxiv.org/abs/1911.02116)/SentencePiece model with a 250k multilingual vocabulary, native dimension 1024.

### On the dimension

`pcaDims` is the one quality knob worth thinking about, and bigger is not better. Distilling bge-m3 at 256 and at 512 gives the same cross-lingual similarity within noise (English/Chinese paraphrase around 0.69 either way), while 512 doubles the matrix on disk and in memory and cuts embedding throughput. PCA to 256 already captures the useful variance of the teacher; the extra dimensions are mostly noise that dilutes the signal. 256 is a good default, and it is where the reference Model2Vec tables (the MinishLab "potion" series) sit too.

## 2. Assemble the model directory

The distiller writes `model.safetensors` (F32), the cleaned `tokenizer.json`, and `config.json`, and copies the teacher's SentencePiece `.model` file when there is one. `DistillModel` assembles and verifies its own output; `AssembleModel` completes a directory put together by hand — for a WordPiece model it derives `vocab.txt` and `tokenizer_config.json` from `tokenizer.json`, for a SentencePiece model it checks that the trained `.model` file is present:

```
opennlp-embeddings AssembleModel -modelDir bge-m3-static
```

A loadable SentencePiece directory then holds:

```
bge-m3-static/
  sentencepiece.bpe.model   # copied from the teacher; segments the text
  tokenizer.json            # Unigram vocab; its row order maps to the matrix
  model.safetensors         # the embedding matrix
  config.json               # carries "normalize": true|false
```

`load` detects the SentencePiece layout from the `.model` file next to `tokenizer.json`; it does not need `tokenizer_config.json`, because the `.model` carries the model's own text normalizer. If you forget the `.model` file, the loader says so by name.

## 3. Load and verify in the JVM

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

Confirm parity against the Python reference before trusting a fresh distillation: embed the same text on both sides and check the vectors match within floating-point tolerance. They should agree to a few parts in ten thousand, because the JVM path reproduces the reference tokenization and pooling exactly, not approximately. The reference Python flow lives in the repository as `dev/embeddings/distill_bge_m3.py`, and `dev/embeddings/parity/` holds a harness that reruns the parity check and the single-thread speed comparison against the Python reference on any machine.

Two tables distilled independently from the same teacher (one with this command, one with Python Model2Vec) agree on their pairwise geometry to a few parts in a thousand — similarities, neighbors, and rankings match — but their raw vectors are not directly comparable axis by axis: PCA fixes only the subspace, and within the near-degenerate tail of the spectrum two independent decompositions choose different bases.

## The WordPiece path

A WordPiece teacher (a BERT-family model such as bge-large-en) distills the same way. Its directory layout is the BERT one instead: `vocab.txt` (one token per line, line number is the row), `model.safetensors`, `config.json`, and `tokenizer_config.json` (whose `do_lower_case` sets the casing). `load` detects WordPiece from the presence of `vocab.txt`.

A distillation writes `tokenizer.json` rather than a `vocab.txt`, so the two BERT files are derived: `vocab.txt` from the `tokenizer.json` vocabulary in id order, `tokenizer_config.json` from the normalizer's lowercase flag (absent, it defaults to lower-casing). `DistillModel` does this itself as its final step; `AssembleModel` is the same step run on its own, for a directory assembled by hand.

## Where a table's license comes from

Distillation carries the teacher's license onto the table. bge-m3 is published under the MIT license per its [model card](https://huggingface.co/BAAI/bge-m3) (verify at download time), so its distillation is freely redistributable; a table distilled from a non-commercial or share-alike teacher inherits those terms. Check the teacher before publishing a table.
