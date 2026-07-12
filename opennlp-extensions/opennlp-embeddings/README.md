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

Embeddings have become an essential part of AI workloads. As such, OpenNLP introduces a pure-JVM approach to embeddings with a modern Model2Vec engine.

Turn text into embedding vectors from a static (non-contextual) table: a per-token vector matrix plus WordPiece tokenization. It is the modern successor to the word2vec and GloVe workflow. Distillation tools can compress a sentence-transformer into such a flat table (the Model2Vec family is the primary target), and looking a sentence up in the table approximates the transformer's semantics at a fraction of the cost. There is no model forward pass, no GPU, and no native runtime; it is pure JVM.

OpenNLP also supports ONNX models, which are inherently more accurate. Model2Vec sacrifices some accuracy for a large speed gain, and OpenNLP recognizes that trade-off, so both embedding methods are supported and share the same `TextEmbedder` seam.

## Quickstart

Point `load` at a downloaded model directory, then embed:

```java
StaticEmbeddingModel model = StaticEmbeddingModel.load(Path.of("/path/to/model-directory"));

float[] vector     = model.embed("The quick brown fox");
double  similarity = model.similarity("coffee", "espresso");
List<Neighbor> near = model.mostSimilar("coffee", 5);
```

The directory is the layout published releases use (`vocab.txt`, `model.safetensors`, `config.json`, `tokenizer_config.json`); the tokenizer and pooling switches are read from the model's own config. One loaded model is immutable and thread-safe, so it can serve every thread of an application.

## When to use it

Reach for this when embedding throughput and deployment simplicity matter more than the last few points of retrieval quality: semantic similarity, deduplication, candidate retrieval in front of a heavier reranker, clustering, or features for a classifier. A contextual model is still the better choice when the task depends on distinguishing word senses in context.

## How it works

A static embedding model is a vocabulary and a matrix: one row per token, each row a vector of the model's dimension. Embedding runs entirely as table lookups and arithmetic:

```mermaid
flowchart LR
  A["text"] --> B["WordPiece tokenize"]
  B --> C["gather token rows<br/>drop unknown, skip special"]
  C --> D["weight + mean-pool"]
  D --> E["L2 normalize"]
  E --> F["float[] vector"]
```

1. **Tokenize.** WordPiece splits the text into subword tokens using the model's own vocabulary and casing rule. Special tokens are marked and never contribute to the pooled vector.
2. **Gather.** Each in-vocabulary token contributes its row from the matrix. Unknown tokens are dropped. A text with no in-vocabulary tokens embeds to a zero vector rather than raising.
3. **Weight and pool.** Per-token weights (when the model carries them) multiply into the running sum, and the sum is divided by the plain token count. This mean-pool matches the reference implementation of the targeted model family exactly, verified against it rather than assumed.
4. **Normalize.** The pooled vector is L2-normalized by default so cosine similarity is a dot product. Normalization can be turned off for models that expect raw pooled vectors.

Per-row L2 norms and the special-token mask are precomputed at load time, so the neighbor scan and similarity calls do not recompute them on every query.

### Loading

The one-argument `load` reads the model's own configuration to resolve the tokenizer and pooling switches, so callers do not restate them:

```mermaid
flowchart TD
  L["StaticEmbeddingModel.load(dir)"] --> CFG["read config.json,<br/>tokenizer_config.json"]
  CFG --> CAS["casing = do_lower_case"]
  CFG --> NRM["normalization"]
  L --> VOC["vocab.txt to WordpieceVocabulary"]
  L --> MAT["model.safetensors to matrix"]
  CAS --> M["immutable, thread-safe model"]
  NRM --> M
  VOC --> M
  MAT --> M
```

The weights are read with a purpose-built **safetensors** reader. Unlike pickle-based checkpoint formats, safetensors carries no executable content, so loading a downloaded file cannot execute arbitrary code. Tensor data streams directly into the decoded array, so the file size is not bound by Java's int-indexed arrays; a single decoded tensor is capped at the maximum Java array length (about 2.1 billion float elements), checked explicitly.

## Architecture

```mermaid
flowchart TD
  subgraph MODEL["StaticEmbeddingModel"]
    WV["WordpieceVocabulary"]
    WP["WordpiecePipeline"]
    MX["embedding matrix"]
  end
  SHP["SafetensorsHeaderParser"] --> SF["SafetensorsFile"]
  SF --> MX
  MODEL -. implements .-> TE["TextEmbedder<br/>(opennlp-api)"]
  DL["SentenceVectorsDL<br/>(opennlp-dl, ONNX)"] -. implements .-> TE
```

`TextEmbedder` is the shared seam: the static path here and the contextual ONNX path in `opennlp-dl` both implement it, so callers can swap one for the other without touching their code.

## Performance

A static table wins on speed and footprint because there is no model forward pass: the hot path is a vocabulary lookup, a handful of vector adds, and one normalization. The module ships a JMH benchmark (`StaticEmbeddingModelBenchmark`) that measures `embed()` and `mostSimilar()` throughput, so you can reproduce numbers on your own hardware and model.

In our measurements on the potion-base-8M distilled table, the JVM path ran roughly an order of magnitude faster single-threaded than the model2vec Python reference on the same table, at around a fifth of the resident memory, with output vectors matching the reference within floating-point tolerance. Parity was established before any of the throughput work, so the speed is not bought with accuracy. Treat these as a starting expectation: results depend on the model, the text length distribution, and the hardware, so run the benchmark on the model you plan to use.

## Usage

### Loading a non-standard layout

For a model laid out differently, the explicit overload takes the two data files and the two model properties directly:

```java
StaticEmbeddingModel model = StaticEmbeddingModel.load(
    Path.of("vocab.txt"), Path.of("model.safetensors"),
    StaticEmbeddingModel.Casing.UNCASED,      // from the model's do_lower_case
    StaticEmbeddingModel.Normalization.L2);   // from the model's config
```

### Neighbors and analogies

`Neighbor` is a small record of the token and its cosine similarity:

```java
for (Neighbor n : model.mostSimilar("coffee", 5)) {
  System.out.println(n.token() + "  " + n.similarity());
}

List<Neighbor> king = model.analogy("man", "king", "woman", 1);
```

### Retrieval

Embed a small corpus once, then rank documents against a query by cosine similarity. Because the vectors are L2-normalized, cosine is a plain dot product:

```java
StaticEmbeddingModel model = StaticEmbeddingModel.load(modelDir);

List<String> docs = List.of(
    "How do I brew espresso at home?",
    "The history of tea in East Asia",
    "Best grinders for pour-over coffee");

float[][] docVectors = docs.stream().map(model::embed).toArray(float[][]::new);
float[]   query      = model.embed("home espresso machine");

IntStream.range(0, docs.size())
    .boxed()
    .sorted(Comparator.comparingDouble(i -> -dot(query, docVectors[i])))
    .forEach(i -> System.out.println(docs.get(i)));
```

Here `dot` is any dot product over two float arrays. For a full RAG-style retriever, keep the document vectors in whatever index you already use and score queries the same way. This applies to most modern search engines, since they tend to decouple the HNSW lookups from the vectors you feed them.

## Getting a model

No model is bundled. Point the module at files you download, and the table's own license applies to the table. The Model2Vec distilled releases (for example potion-base-8M) publish the exact directory layout the one-argument `load` expects: download that release's `vocab.txt`, `model.safetensors`, `config.json`, and `tokenizer_config.json` into one directory and pass the directory to `load`.

## Notes and limits

- Instances are immutable and safe for concurrent use, so one loaded model serves every thread.
- Static tables do not disambiguate word senses in context. If the task turns on context, use a contextual model.
- Out-of-vocabulary-only input embeds to a zero vector, matching the reference implementation. This is a rare edge case for text in the model's language (WordPiece backs off to subwords), and mostly happens for empty input or text outside the vocabulary's coverage. Decide in your code whether a zero vector means "no signal" for your use case.

## See also

- The Dev Manual chapter (`opennlp-docs/src/docbkx/embeddings.xml`) for the same material in the manual.
- `opennlp-dl` for the contextual, ONNX-backed sentence vector path, which shares the `TextEmbedder` interface with this module.
