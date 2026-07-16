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

Turn text into embedding vectors from a static (non-contextual) table: a per-token vector matrix plus subword tokenization, WordPiece or SentencePiece. It uses the same lookup-table approach as [word2vec](https://code.google.com/archive/p/word2vec/) and [GloVe](https://nlp.stanford.edu/projects/glove/). Distillation tools can compress a sentence-transformer into such a flat table (the [Model2Vec](https://github.com/MinishLab/model2vec) family is the primary target), and looking a sentence up in the table approximates the transformer's semantics at a fraction of the cost. Because SentencePiece models are supported, this includes multilingual tables distilled from encoders like the [XLM-RoBERTa](https://arxiv.org/abs/1911.02116) family. There is no model forward pass, no GPU, and no native runtime; it is pure JVM.

OpenNLP also supports ONNX models, which are inherently more accurate. Model2Vec sacrifices some accuracy for a large speed gain, and OpenNLP recognizes that trade-off, so both embedding methods are supported and implement the same `TextEmbedder` interface.

## Quickstart

Point `load` at a downloaded model directory, then embed:

```java
StaticEmbeddingModel model = StaticEmbeddingModel.load(Path.of("/path/to/model-directory"));

float[] vector     = model.embed("The quick brown fox");
double  similarity = model.similarity("coffee", "espresso");
List<Neighbor> near = model.mostSimilar("coffee", 5);
```

The directory is the layout published releases use, and `load` detects the tokenizer family from the files present. A WordPiece model carries `vocab.txt`, `model.safetensors`, `config.json`, and `tokenizer_config.json`. A SentencePiece model carries a trained `.model` file (`sentencepiece.bpe.model`, `spiece.model`, or `tokenizer.model`) next to `tokenizer.json`, `model.safetensors`, and `config.json`. In both cases the tokenizer and pooling switches are read from the model's own config. One loaded model is immutable and thread-safe, so it can serve every thread of an application.

A multilingual SentencePiece table embeds different languages into the same space, so similarity works across them:

```java
model.similarity("The weather is beautiful today", "今天天气很好");  // same meaning, high score
```

## When to use it

Reach for this when embedding throughput and deployment simplicity matter more than the last few points of retrieval quality: semantic similarity, deduplication, candidate retrieval in front of a heavier reranker, clustering, or features for a classifier. A contextual model is still the better choice when the task depends on distinguishing word senses in context.

## How it works

A static embedding model is a vocabulary and a matrix: one row per token, each row a vector of the model's dimension. Embedding runs entirely as table lookups and arithmetic:

```mermaid
flowchart LR
  A["text"] --> B["subword tokenize<br/>(WordPiece or SentencePiece)"]
  B --> C["gather piece rows by string<br/>drop unknown, skip special"]
  C --> D["weight + mean-pool"]
  D --> E["L2 normalize"]
  E --> F["float[] vector"]
```

1. **Tokenize.** The model's own subword tokenizer splits the text into pieces: WordPiece with the model's casing rule, or a trained SentencePiece model that carries its own text normalizer. Special pieces (the WordPiece `[CLS]`, `[SEP]`, and `[UNK]` tokens, a SentencePiece model's control and unknown pieces) never contribute to the pooled vector.
2. **Gather.** Each piece contributes its matrix row, found by the piece *string* rather than the tokenizer's numeric id. The two files of a SentencePiece model routinely order and offset their ids differently (the fairseq convention shifts them by one, and distillation tools reorder the vocabulary outright), so string lookup is what keeps the pairing robust; a poolable piece with no matrix row fails loud at load time, not at query time. Unknown pieces are dropped, and a text with no in-vocabulary pieces embeds to a zero vector rather than raising.
3. **Weight and pool.** Per-token weights (when the model carries them) multiply into the running sum, and the sum is divided by the plain token count. This mean-pool matches the reference implementation of the targeted model family exactly, verified against it rather than assumed.
4. **Normalize.** The pooled vector is L2-normalized by default so cosine similarity is a dot product. Normalization can be turned off for models that expect raw pooled vectors.

Per-row L2 norms and the special-token mask are precomputed at load time, so the neighbor scan and similarity calls do not recompute them on every query.

### Loading

The one-argument `load` reads the model's own configuration to resolve the tokenizer and pooling switches, so callers do not restate them:

```mermaid
flowchart TD
  L["StaticEmbeddingModel.load(dir)"] --> DET{"vocab.txt present?"}
  DET -- "yes: WordPiece" --> WCFG["read config.json,<br/>tokenizer_config.json"]
  WCFG --> CAS["casing = do_lower_case"]
  DET -- "no: SentencePiece" --> SPM["load the trained .model<br/>(its own normalizer, no casing switch)"]
  SPM --> TJ["tokenizer.json vocab<br/>names the matrix rows"]
  TJ --> COV["verify every poolable piece<br/>has a matrix row"]
  L --> NRM["normalization from config.json"]
  L --> MAT["model.safetensors to matrix"]
  CAS --> M["immutable, thread-safe model"]
  COV --> M
  NRM --> M
  MAT --> M
```

The weights are read with a purpose-built [safetensors](https://github.com/huggingface/safetensors) reader. Unlike pickle-based checkpoint formats, safetensors carries no executable content, so loading a downloaded file cannot execute arbitrary code. Tensor data streams directly into the decoded array, so the file size is not bound by Java's int-indexed arrays; a single decoded tensor is capped at the maximum Java array length (about 2.1 billion float elements), checked explicitly.

## Architecture

```mermaid
flowchart TD
  subgraph MODEL["StaticEmbeddingModel"]
    EV["EmbeddingVocabulary<br/>(piece string to matrix row)"]
    ST["SubwordTokenizer"]
    MX["embedding matrix"]
  end
  WE["WordpieceEncoder<br/>(opennlp-api)"] -. one of .-> ST
  SP["SentencePieceTokenizer<br/>(opennlp-subword)"] -. one of .-> ST
  SHP["SafetensorsHeaderParser"] --> SF["SafetensorsFile"]
  SF --> MX
  MODEL -. implements .-> TE["TextEmbedder<br/>(opennlp-api)"]
  DL["SentenceVectorsDL<br/>(opennlp-dl, ONNX)"] -. implements .-> TE
```

Two interfaces keep the module small. `SubwordTokenizer` is the tokenization interface: the WordPiece encoder from `opennlp-api` and the pure-JVM SentencePiece implementation from `opennlp-subword` both produce the same piece stream, so the pooling code has exactly one path. `TextEmbedder` is the embedding interface: the static path here and the contextual ONNX path in `opennlp-dl` both implement it, so callers can swap one for the other without touching their code.

## Performance

A static table wins on speed and footprint because there is no model forward pass: the hot path is a vocabulary lookup, a handful of vector adds, and one normalization. The module ships a Java Microbenchmark Harness (JMH) benchmark (`StaticEmbeddingModelBenchmark`) that measures `embed()` and `mostSimilar()` throughput on a real model directory (`-p modelDir=/path/to/model`), so you can reproduce numbers on your own hardware and model.

Two things drive the numbers, and the benchmark separates them. `embed()` is tokenize-and-pool, so its cost tracks the text and the tokenizer, not the table size. `mostSimilar()` is a brute-force scan over every row, so its cost tracks the vocabulary size directly. A run comparing a small WordPiece table against the large multilingual SentencePiece table makes the split visible (throughput across all cores, one machine, indicative not publishable):

| table | tokenizer, rows | `embed()` | `mostSimilar()` |
| --- | --- | --- | --- |
| potion-base-8M | WordPiece, 29.5k | ~295k ops/s | ~9,000 ops/s |
| bge-m3 (distilled) | SentencePiece, 250k | ~1.47M ops/s | ~550 ops/s |

So a large multilingual vocabulary is free for embedding and expensive for a full nearest-neighbor scan; that scan is where an approximate index earns its place once the table is large. Separately, `scripts/parity/` holds a harness that reruns the single-thread speed comparison against the Model2Vec Python reference and checks that the output vectors match it within floating-point tolerance, so a cross-runtime comparison is something you reproduce on your own hardware rather than quote. Treat all of these as a starting expectation and run the benchmark on the model you plan to use.

## Usage

### Loading a non-standard layout

For a model laid out differently, the explicit overloads take the data files and the model properties directly. WordPiece:

```java
StaticEmbeddingModel model = StaticEmbeddingModel.load(
    Path.of("vocab.txt"), Path.of("model.safetensors"),
    StaticEmbeddingModel.Casing.UNCASED,      // from the model's do_lower_case
    StaticEmbeddingModel.Normalization.L2);   // from the model's config
```

SentencePiece (no casing switch, because the `.model` file carries the model's own text normalizer):

```java
StaticEmbeddingModel model = StaticEmbeddingModel.loadSentencePiece(
    Path.of("sentencepiece.bpe.model"), Path.of("tokenizer.json"),
    Path.of("model.safetensors"),
    StaticEmbeddingModel.Normalization.L2);
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

Here `dot` is any dot product over two float arrays. For a full retrieval-augmented generation (RAG) retriever, keep the document vectors in whatever index you already use and score queries the same way. A vector index that stores and searches precomputed vectors, such as a Hierarchical Navigable Small World (HNSW) index, does not care how those vectors were produced, so these embeddings can feed it directly.

## Getting a model

No model is bundled. Point the module at files you download, and the table's own license applies to the table. The Model2Vec distilled releases (for example potion-base-8M) publish the exact directory layout the one-argument `load` expects: download that release's `vocab.txt`, `model.safetensors`, `config.json`, and `tokenizer_config.json` into one directory and pass the directory to `load`.

For a multilingual SentencePiece table (for example one distilled from a bge-m3 or XLM-RoBERTa teacher), the distillation output ships `tokenizer.json`, `model.safetensors`, and `config.json` but usually not the trained SentencePiece `.model` file; copy that one file from the teacher model's own repository (it is named `sentencepiece.bpe.model` there) into the same directory. The loader tells you exactly this if the file is missing.

## Notes and limits

- Instances are immutable and safe for concurrent use, so one loaded model serves every thread.
- Static tables do not disambiguate word senses in context. If the task turns on context, use a contextual model.
- Out-of-vocabulary-only input embeds to a zero vector, matching the reference implementation. This is a rare edge case for text in the model's language (WordPiece backs off to subwords), and mostly happens for empty input or text outside the vocabulary's coverage. Decide in your code whether a zero vector means "no signal" for your use case.

## See also

- [`TRAINING.md`](TRAINING.md) for distilling your own table from a sentence-transformer teacher, including the multilingual SentencePiece worked example.
- The Dev Manual chapter (`opennlp-docs/src/docbkx/embeddings.xml`) for the same material in the manual.
- `opennlp-dl` for the contextual, ONNX-backed sentence vector path, which shares the `TextEmbedder` interface with this module.
