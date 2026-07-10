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

# OpenNLP Static Embeddings

This module produces sentence and word embedding vectors from a static (non-contextual) embedding table: a per-token vector matrix plus WordPiece tokenization, the modern successor to the word2vec and GloVe workflow. Distillation tools can compress a sentence-transformer into such a flat table (the Model2Vec family of releases is the primary target), and looking a sentence up in the table approximates the transformer's semantics at a small fraction of the cost: embedding a text is tokenize, gather, mean-pool, and normalize. No model forward pass, no GPU, no native runtime, pure JVM.

## When to use it

Use this module when embedding throughput and deployment simplicity matter more than the last few points of retrieval quality: semantic similarity and deduplication, candidate retrieval for a heavier reranker, clustering, or classification features. A contextual model remains the better choice when distinguishing word senses in context is the point of the task.

## Usage

A downloaded model directory containing `vocab.txt`, `model.safetensors`, `config.json`, and `tokenizer_config.json` (the layout published releases use) loads with one call; the tokenizer and pooling switches are read from the model's own configuration:

```java
StaticEmbeddingModel model = StaticEmbeddingModel.load(Path.of("/path/to/model-directory"));

float[] vector = model.embed("The quick brown fox");
double similarity = model.similarity("coffee", "espresso");
List<Neighbor> neighbors = model.mostSimilar("coffee", 5);
List<Neighbor> analogy = model.analogy("man", "king", "woman", 1);
```

For a model laid out differently, the explicit overload takes the two data files and the two model properties directly:

```java
StaticEmbeddingModel model = StaticEmbeddingModel.load(
    Path.of("vocab.txt"), Path.of("model.safetensors"),
    StaticEmbeddingModel.Casing.UNCASED,      // from the model's do_lower_case
    StaticEmbeddingModel.Normalization.L2);   // from the model's config
```

Instances are immutable and safe for concurrent use, so one loaded model can serve every thread of an application. Texts with no in-vocabulary tokens embed to a zero vector rather than raising an error.

## Notes

- No model is bundled. Callers point the module at files they downloaded, and the table's own license applies to the table.
- Weights are read with a purpose-built safetensors reader. Unlike pickle-based checkpoint formats, safetensors carries no executable content, so loading a file cannot execute arbitrary code. Tensor data streams directly into the decoded array, so file size is not limited by Java's int-indexed arrays; one decoded tensor is capped at the maximum Java array length (about 2.1 billion float elements), checked explicitly.
- The pooling formula matches the reference implementations of the targeted model family exactly (verified against them, not assumed): special tokens never pool, unknown tokens are dropped, per-token weights multiply into the sum, and the sum divides by the plain token count.
