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

The `DistillModel` command produces a static embedding table from a sentence-transformer teacher. It follows the [Model2Vec](https://github.com/MinishLab/model2vec) pipeline: run the teacher's ONNX graph over its vocabulary, apply principal component analysis (PCA) and Zipf weighting, then write a flat per-token matrix. This is a single pass over the vocabulary, without a training corpus or optimization loop.

## 1. Distill the teacher

```
opennlp-embeddings DistillModel -teacher BAAI/bge-m3 -out bge-m3-static -pcaDims 256
```

`-teacher` is a Hugging Face model id or a local directory. A remote teacher is cached under `~/.cache/opennlp-embeddings/<org>-<model>` with its `tokenizer.json`, optional `tokenizer_config.json`, `onnx/model.onnx`, optional `onnx/model.onnx_data`, and SentencePiece model when required. `-pcaDims` defaults to 256. The command assembles the output directory and verifies it with `StaticEmbeddingModel.load` before printing its summary.

The command replaces model and tokenizer files produced by an earlier distillation in the output directory. Unrelated files remain. An interrupted run may leave an incomplete output directory, so rerun the command before loading it.

bge-m3 is an [XLM-RoBERTa](https://arxiv.org/abs/1911.02116)/SentencePiece model with a 250k multilingual vocabulary, native dimension 1024.

### On the dimension

`pcaDims` controls the output vector width and defaults to 256. A larger value increases the model's memory, disk, and inference cost. Evaluate retrieval or classification quality on the target task before changing it.

## 2. Assemble the model directory

The distiller writes `model.safetensors` (F32), the cleaned `tokenizer.json`, and `config.json`, and copies the teacher's SentencePiece `.model` file when there is one. `DistillModel` assembles and verifies its own output. `AssembleModel` completes a directory put together by hand: for a WordPiece model it derives `vocab.txt` and `tokenizer_config.json` from `tokenizer.json`; for a SentencePiece model it checks that the trained `.model` file is present:

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

double crossLingual = model.similarity(
    "The weather is beautiful today", "今天天气很好");
double unrelated = model.similarity(
    "The weather is beautiful today", "quarterly earnings missed");

List<Neighbor> neighbors = model.mostSimilar("coffee", 5);
```

The reference Python flow lives in `dev/embeddings/distill_bge_m3.py`. The `dev/embeddings/parity/` harness embeds the same text with both implementations and reports vector differences and single-thread speed. Compare independently distilled tables by similarities and rankings because PCA bases can differ.

## The WordPiece path

A WordPiece teacher (a BERT-family model such as bge-large-en) distills the same way. Its directory layout is the BERT one instead: `vocab.txt` (one token per line, line number is the row), `model.safetensors`, `config.json`, and `tokenizer_config.json` (whose `do_lower_case` sets the casing). `load` detects WordPiece from the presence of `vocab.txt`.

A distillation writes `tokenizer.json` rather than a `vocab.txt`, so the two BERT files are derived: `vocab.txt` from the `tokenizer.json` vocabulary in id order, `tokenizer_config.json` from the normalizer's lowercase flag (absent, it defaults to lower-casing). `DistillModel` does this itself as its final step; `AssembleModel` is the same step run on its own, for a directory assembled by hand.

## Where a table's license comes from

Check the teacher model's license before publishing a distilled table. Record the exact teacher revision and retain any attribution or redistribution terms that apply to derived weights.
