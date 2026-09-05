/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.embeddings.eval;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import opennlp.embeddings.QuantizedEmbeddingMatrix;
import opennlp.embeddings.StaticEmbeddingModel;
import opennlp.embeddings.corpus.CasePassage;
import opennlp.embeddings.corpus.DictionaryEntry;
import opennlp.embeddings.index.FlatFloatIndex;
import opennlp.embeddings.index.TurboQuantIndex;
import opennlp.embeddings.index.VectorIndex;
import opennlp.tools.util.java.Experimental;

/**
 * Evaluates exact and quantized vector search over embedded passages and dictionary entries
 * without labeled relevance judgments.
 *
 * <p>Each evaluation derives its target from the input:</p>
 * <ol>
 *   <li><b>Index fidelity</b>: every indexable passage vector queries both indexes; the
 *   quantized index's overlap with the exact top-k and its rank-1 agreement measure pure
 *   quantization loss.</li>
 *   <li><b>Definition to headword</b>: each dictionary definition queries an index of headword
 *   embeddings; the definition's own headword is the relevant answer.</li>
 *   <li><b>Half passage</b>: the first half of each passage queries the passage index; the
 *   passage itself is the relevant answer.</li>
 * </ol>
 *
 * <p>The caller supplies the quantization seed, and queries retain input order. Timing values
 * depend on the runtime environment.</p>
 *
 * <p>Warning: Experimental new feature; the API might change in a later release.</p>
 */
@Experimental
public final class SearchEvaluator {

  private static final String EXACT_INDEX_NAME = "exact";
  private static final String QUANTIZED_INDEX_NAME = "turboquant";

  /** Not instantiable. */
  private SearchEvaluator() {
  }

  /**
   * The build and throughput measurements of one index.
   *
   * @param name                  The index's display name.
   * @param rows                  The number of indexed vectors.
   * @param storageBytesPerVector The index's reported storage cost of one vector.
   * @param buildMillis           The index build time in milliseconds.
   * @param queriesPerSecond      Single-thread queries per second, measured after a warm-up pass.
   */
  public record IndexMetrics(String name, int rows, double storageBytesPerVector,
                             long buildMillis,
                             double queriesPerSecond) {

    /**
     * Validates one index measurement.
     *
     * @throws IllegalArgumentException Thrown if a value is invalid.
     */
    public IndexMetrics {
      requireName(name);
      requireNonNegative(rows, "rows");
      requireNonNegative(storageBytesPerVector, "storageBytesPerVector");
      requireNonNegative(buildMillis, "buildMillis");
      requireNonNegative(queriesPerSecond, "queriesPerSecond");
    }
  }

  /**
   * The outcome of one retrieval evaluation on one index.
   *
   * @param name      The index's display name.
   * @param queries   The number of queries with a usable (non-zero) embedding.
   * @param mrr       The mean reciprocal rank at the evaluated depth; a target beyond the depth
   *                  contributes zero.
   * @param recallAt1 The share of queries whose target ranked first.
   * @param recallAtK The share of queries whose target ranked within the evaluated depth.
   */
  public record RetrievalMetrics(String name, int queries, double mrr, double recallAt1,
                                 double recallAtK) {

    /**
     * Validates one retrieval measurement.
     *
     * @throws IllegalArgumentException Thrown if a value is invalid.
     */
    public RetrievalMetrics {
      requireName(name);
      requireNonNegative(queries, "queries");
      requireRatio(mrr, "mrr");
      requireRatio(recallAt1, "recallAt1");
      requireRatio(recallAtK, "recallAtK");
      if (queries == 0 && (mrr != 0 || recallAt1 != 0 || recallAtK != 0)) {
        throw new IllegalArgumentException("metrics must be zero when queries is zero");
      }
      if (recallAt1 > mrr || mrr > recallAtK) {
        throw new IllegalArgumentException(
            "metrics must satisfy recallAt1 <= mrr <= recallAtK");
      }
    }
  }

  /**
   * One complete evaluation run.
   *
   * @param passageCount        The total number of supplied passages.
   * @param indexedPassageCount The number of passages with a usable embedding.
   * @param headwordCount       The total number of supplied dictionary headwords.
   * @param indexedHeadwordCount The number of headwords with a usable embedding.
   * @param dimension           The embedding dimension.
   * @param vocabularySize      The model's subword vocabulary size.
   * @param termCount           The model's term-row count.
   * @param bits                The quantization bit width.
   * @param topK                The evaluated depth.
   * @param embedMillis         The time to embed every passage, in milliseconds.
   * @param flat                The exact passage index's build and throughput metrics.
   * @param quantized           The quantized passage index's build and throughput metrics.
   * @param fidelityRecallAtK   The quantized index's mean overlap with the exact top-k.
   * @param fidelityAgreement   The share of queries where both indexes rank the same id first.
   * @param definitionToHeadword The definition-to-headword metrics, exact then quantized.
   * @param halfPassage         The half-passage metrics, exact then quantized.
   */
  public record Report(int passageCount, int indexedPassageCount,
                       int headwordCount, int indexedHeadwordCount,
                       int dimension, int vocabularySize, int termCount,
                       int bits, int topK, long embedMillis,
                       IndexMetrics flat, IndexMetrics quantized,
                       double fidelityRecallAtK, double fidelityAgreement,
                       List<RetrievalMetrics> definitionToHeadword,
                       List<RetrievalMetrics> halfPassage) {

    /**
     * Validates and copies one complete report.
     *
     * @throws IllegalArgumentException Thrown if a value is invalid.
     */
    public Report {
      requirePositive(passageCount, "passageCount");
      requirePositive(indexedPassageCount, "indexedPassageCount");
      requireRange(indexedPassageCount, passageCount, "indexedPassageCount", "passageCount");
      requireNonNegative(headwordCount, "headwordCount");
      requireNonNegative(indexedHeadwordCount, "indexedHeadwordCount");
      requireRange(indexedHeadwordCount, headwordCount, "indexedHeadwordCount", "headwordCount");
      requirePositive(dimension, "dimension");
      requireNonNegative(vocabularySize, "vocabularySize");
      requireNonNegative(termCount, "termCount");
      if (bits < QuantizedEmbeddingMatrix.MIN_BITS
          || bits > QuantizedEmbeddingMatrix.MAX_BITS) {
        throw new IllegalArgumentException("bits must be between "
            + QuantizedEmbeddingMatrix.MIN_BITS + " and "
            + QuantizedEmbeddingMatrix.MAX_BITS + ": " + bits);
      }
      requirePositive(topK, "topK");
      requireNonNegative(embedMillis, "embedMillis");
      if (flat == null) {
        throw new IllegalArgumentException("flat must not be null");
      }
      if (quantized == null) {
        throw new IllegalArgumentException("quantized must not be null");
      }
      if (flat.rows() != indexedPassageCount || quantized.rows() != indexedPassageCount) {
        throw new IllegalArgumentException(
            "index row counts must equal indexedPassageCount: " + indexedPassageCount);
      }
      requireRatio(fidelityRecallAtK, "fidelityRecallAtK");
      requireRatio(fidelityAgreement, "fidelityAgreement");
      definitionToHeadword = copyMetrics(definitionToHeadword, "definitionToHeadword");
      halfPassage = copyMetrics(halfPassage, "halfPassage");
    }

    /** {@return the human-readable report as GitHub-flavored markdown} */
    public String toMarkdown() {
      final StringBuilder md = new StringBuilder(4096);
      md.append("# Vector search evaluation\n\n");
      md.append("Model: ").append(vocabularySize).append(" subword rows, ")
          .append(termCount).append(" term rows, dimension ").append(dimension)
          .append(". Corpus: ").append(passageCount).append(" passages (")
          .append(indexedPassageCount).append(" indexable), ")
          .append(headwordCount).append(" dictionary headwords (")
          .append(indexedHeadwordCount).append(" indexable). Quantization: ")
          .append(bits).append(" bits per dimension. Evaluation depth: top ")
          .append(topK).append(".\n\n");
      md.append("Embedding the passages took ").append(embedMillis).append(" ms.\n\n");

      md.append("## Passage index build and throughput\n\n");
      md.append("| index | rows | storage bytes/vector | build (ms) | QPS (1 thread) |\n");
      md.append("|---|---|---|---|---|\n");
      for (final IndexMetrics index : List.of(flat, quantized)) {
        md.append("| ").append(index.name())
            .append(" | ").append(index.rows())
            .append(" | ").append(format(index.storageBytesPerVector()))
            .append(" | ").append(index.buildMillis())
            .append(" | ").append(String.format(Locale.ROOT, "%.0f", index.queriesPerSecond()))
            .append(" |\n");
      }
      md.append('\n');

      md.append("## Index fidelity (quantized vs exact, passages as queries)\n\n");
      md.append("| metric | value |\n|---|---|\n");
      md.append("| eligible queries | ").append(indexedPassageCount).append(" |\n");
      md.append("| recall@").append(topK).append(" vs exact | ")
          .append(format(fidelityRecallAtK)).append(" |\n");
      md.append("| rank-1 agreement | ").append(format(fidelityAgreement)).append(" |\n\n");

      appendRetrieval(md, "Definition to headword retrieval", definitionToHeadword);
      appendRetrieval(md, "Half-passage retrieval", halfPassage);
      return md.toString();
    }

    /**
     * Appends one retrieval section.
     *
     * @param md      The markdown under construction.
     * @param title   The section title.
     * @param metrics The per-index metrics.
     */
    private void appendRetrieval(StringBuilder md, String title,
                                 List<RetrievalMetrics> metrics) {
      md.append("## ").append(title).append("\n\n");
      md.append("| index | queries | MRR@").append(topK)
          .append(" | recall@1 | recall@").append(topK).append(" |\n");
      md.append("|---|---|---|---|---|\n");
      for (final RetrievalMetrics m : metrics) {
        md.append("| ").append(m.name())
            .append(" | ").append(m.queries())
            .append(" | ").append(format(m.mrr()))
            .append(" | ").append(format(m.recallAt1()))
            .append(" | ").append(format(m.recallAtK()))
            .append(" |\n");
      }
      md.append('\n');
    }

    /** {@return the machine-readable report: one {@code key<TAB>value} line per metric} */
    public String toTsv() {
      final StringBuilder tsv = new StringBuilder(1024);
      line(tsv, "passages", passageCount);
      line(tsv, "passages.indexed", indexedPassageCount);
      line(tsv, "headwords", headwordCount);
      line(tsv, "headwords.indexed", indexedHeadwordCount);
      line(tsv, "dimension", dimension);
      line(tsv, "vocabulary.subwords", vocabularySize);
      line(tsv, "vocabulary.terms", termCount);
      line(tsv, "bits", bits);
      line(tsv, "topK", topK);
      line(tsv, "embed.millis", embedMillis);
      for (final IndexMetrics index : List.of(flat, quantized)) {
        line(tsv, index.name() + ".storageBytesPerVector",
            format(index.storageBytesPerVector()));
        line(tsv, index.name() + ".build.millis", index.buildMillis());
        line(tsv, index.name() + ".qps", String.format(Locale.ROOT, "%.0f",
            index.queriesPerSecond()));
      }
      line(tsv, "fidelity.recallAtK", format(fidelityRecallAtK));
      line(tsv, "fidelity.rank1Agreement", format(fidelityAgreement));
      line(tsv, "fidelity.queries", indexedPassageCount);
      for (final RetrievalMetrics m : definitionToHeadword) {
        retrievalLines(tsv, "definitionToHeadword." + m.name(), m);
      }
      for (final RetrievalMetrics m : halfPassage) {
        retrievalLines(tsv, "halfPassage." + m.name(), m);
      }
      return tsv.toString();
    }

    /**
     * Appends the three metric lines of one retrieval outcome.
     *
     * @param tsv    The TSV under construction.
     * @param prefix The metric key prefix.
     * @param m      The metrics.
     */
    private void retrievalLines(StringBuilder tsv, String prefix, RetrievalMetrics m) {
      line(tsv, prefix + ".queries", m.queries());
      line(tsv, prefix + ".mrr", format(m.mrr()));
      line(tsv, prefix + ".recallAt1", format(m.recallAt1()));
      line(tsv, prefix + ".recallAtK", format(m.recallAtK()));
    }

    /**
     * Appends one {@code key<TAB>value} line.
     *
     * @param tsv   The TSV under construction.
     * @param key   The metric key.
     * @param value The metric value.
     */
    private void line(StringBuilder tsv, String key, Object value) {
      tsv.append(key).append('\t').append(value).append('\n');
    }

    /** {@return a ratio formatted with three decimals, locale-independent} */
    private String format(double value) {
      return String.format(Locale.ROOT, "%.3f", value);
    }
  }

  /**
   * Runs the full evaluation.
   *
   * @param model      The embedding model. Must not be {@code null}.
   * @param passages   The passages to index and query. Must not be {@code null} or empty, must
   *                   contain no {@code null} elements, and must have unique ids. At least one
   *                   passage text must produce a usable embedding.
   * @param dictionary The dictionary entries for the definition-to-headword evaluation. Must
   *                   not be {@code null}, must contain no {@code null} elements, and must have
   *                   unique headwords. It may be empty, which skips that evaluation's queries.
   * @param bits       The quantization bit width, as in
   *                   {@link TurboQuantIndex#TurboQuantIndex(int, int, long)}.
   * @param seed       The quantization rotation seed.
   * @param topK       The evaluation depth. Must be at least 1.
   * @return The report.
   * @throws IllegalArgumentException Thrown if an argument violates the constraints above or is
   *     out of range.
   */
  public static Report run(StaticEmbeddingModel model, List<CasePassage> passages,
                           List<DictionaryEntry> dictionary, int bits, long seed, int topK) {
    if (model == null) {
      throw new IllegalArgumentException("model must not be null");
    }
    if (passages == null || passages.isEmpty()) {
      throw new IllegalArgumentException("passages must not be null or empty");
    }
    if (dictionary == null) {
      throw new IllegalArgumentException("dictionary must not be null");
    }
    if (bits < QuantizedEmbeddingMatrix.MIN_BITS
        || bits > QuantizedEmbeddingMatrix.MAX_BITS) {
      throw new IllegalArgumentException("bits must be between "
          + QuantizedEmbeddingMatrix.MIN_BITS + " and "
          + QuantizedEmbeddingMatrix.MAX_BITS + ": " + bits);
    }
    if (topK < 1) {
      throw new IllegalArgumentException("topK must be at least 1: " + topK);
    }
    validateInputs(passages, dictionary);

    final long embedStart = System.nanoTime();
    final float[][] passageVectors = new float[passages.size()][];
    final boolean[] indexedPassages = new boolean[passages.size()];
    final List<float[]> fidelityQueries = new ArrayList<>(passages.size());
    for (int i = 0; i < passages.size(); i++) {
      passageVectors[i] = model.embed(passages.get(i).text());
      if (hasDirection(passageVectors[i])) {
        indexedPassages[i] = true;
        fidelityQueries.add(passageVectors[i]);
      }
    }
    if (fidelityQueries.isEmpty()) {
      throw new IllegalArgumentException(
          "passages must contain at least one text with a usable embedding");
    }
    final long embedMillis = millisSince(embedStart);

    final FlatFloatIndex flat = new FlatFloatIndex(model.dimension());
    final TurboQuantIndex quantized = new TurboQuantIndex(model.dimension(), bits, seed);
    for (int i = 0; i < passages.size(); i++) {
      if (!indexedPassages[i]) {
        continue;
      }
      flat.add(passages.get(i).id(), passageVectors[i]);
      quantized.add(passages.get(i).id(), passageVectors[i]);
    }
    final long flatBuildStart = System.nanoTime();
    flat.freeze();
    final long flatBuildMillis = millisSince(flatBuildStart);
    final long quantizedBuildStart = System.nanoTime();
    quantized.freeze();
    final long quantizedBuildMillis = millisSince(quantizedBuildStart);

    // Fidelity: quantized against exact on every indexable passage vector; this also warms both
    // indexes for the throughput measurement after it.
    final Fidelity fidelity = fidelity(flat, quantized, fidelityQueries, topK);

    final float[][] timedQueries = fidelityQueries.toArray(float[][]::new);

    final IndexMetrics flatMetrics = new IndexMetrics(EXACT_INDEX_NAME, flat.size(),
        model.dimension() * (double) Float.BYTES, flatBuildMillis,
        queriesPerSecond(flat, timedQueries, topK));
    final IndexMetrics quantizedMetrics = new IndexMetrics(
        QUANTIZED_INDEX_NAME, quantized.size(),
        quantized.bytesPerVector(), quantizedBuildMillis,
        queriesPerSecond(quantized, timedQueries, topK));

    // Definition to headword: an index of headword embeddings queried by definitions.
    final List<RetrievalMetrics> definitionToHeadword = new ArrayList<>(2);
    int indexedHeadwordCount = 0;
    if (!dictionary.isEmpty()) {
      final FlatFloatIndex flatHeadwords = new FlatFloatIndex(model.dimension());
      final TurboQuantIndex quantizedHeadwords =
          new TurboQuantIndex(model.dimension(), bits, seed);
      final List<DictionaryEntry> indexedHeadwords = new ArrayList<>(dictionary.size());
      for (final DictionaryEntry entry : dictionary) {
        final float[] vector = model.embed(entry.headword());
        if (!hasDirection(vector)) {
          continue;
        }
        flatHeadwords.add(entry.headword(), vector);
        quantizedHeadwords.add(entry.headword(), vector);
        indexedHeadwords.add(entry);
      }
      flatHeadwords.freeze();
      quantizedHeadwords.freeze();
      indexedHeadwordCount = indexedHeadwords.size();
      final List<String> targets = new ArrayList<>(indexedHeadwordCount);
      final List<float[]> queries = new ArrayList<>(indexedHeadwordCount);
      for (final DictionaryEntry entry : indexedHeadwords) {
        queries.add(model.embed(entry.definition()));
        targets.add(entry.headword());
      }
      definitionToHeadword.add(
          retrieval(EXACT_INDEX_NAME, flatHeadwords, queries, targets, topK));
      definitionToHeadword.add(
          retrieval(QUANTIZED_INDEX_NAME, quantizedHeadwords, queries, targets, topK));
    }

    // Half passage: the first half of each passage queries the passage index.
    final List<String> passageTargets = new ArrayList<>(passages.size());
    final List<float[]> halfQueries = new ArrayList<>(passages.size());
    for (int i = 0; i < passages.size(); i++) {
      if (!indexedPassages[i]) {
        continue;
      }
      final CasePassage passage = passages.get(i);
      halfQueries.add(model.embed(firstHalf(passage.text())));
      passageTargets.add(passage.id());
    }
    final List<RetrievalMetrics> halfPassage = List.of(
        retrieval(EXACT_INDEX_NAME, flat, halfQueries, passageTargets, topK),
        retrieval(QUANTIZED_INDEX_NAME, quantized, halfQueries, passageTargets, topK));

    return new Report(passages.size(), fidelityQueries.size(),
        dictionary.size(), indexedHeadwordCount, model.dimension(),
        model.vocabularySize(), model.termCount(), bits, topK, embedMillis,
        flatMetrics, quantizedMetrics, fidelity.recallAtK(), fidelity.rank1Agreement(),
        List.copyOf(definitionToHeadword), halfPassage);
  }

  /**
   * An approximate index's agreement with the exact one on the same queries.
   *
   * @param recallAtK      The mean overlap between the two top-k result sets, relative to the
   *                       number of exact results returned.
   * @param rank1Agreement The share of queries where both indexes rank the same id first.
   */
  record Fidelity(double recallAtK, double rank1Agreement) {
  }

  /**
   * Measures an approximate index against the exact one: each query runs against both, and the
   * approximate results are scored by their overlap with the exact top {@code topK}.
   *
   * @param exact       The exact index.
   * @param approximate The approximate index over the same vectors.
   * @param queries     The query vectors; every query must have a direction.
   * @param topK        The evaluation depth.
   * @return The fidelity measurements.
   */
  static Fidelity fidelity(VectorIndex exact, VectorIndex approximate,
                           List<float[]> queries, int topK) {
    long overlap = 0;
    long exactResultCount = 0;
    int agreement = 0;
    for (final float[] query : queries) {
      final List<VectorIndex.Hit> truth = exact.topK(query, topK);
      final List<VectorIndex.Hit> answer = approximate.topK(query, topK);
      exactResultCount += truth.size();
      final Set<String> truthIds = new HashSet<>(truth.size() * 2);
      for (final VectorIndex.Hit hit : truth) {
        truthIds.add(hit.id());
      }
      for (final VectorIndex.Hit hit : answer) {
        if (truthIds.contains(hit.id())) {
          overlap++;
        }
      }
      if (!truth.isEmpty() && !answer.isEmpty()
          && truth.get(0).id().equals(answer.get(0).id())) {
        agreement++;
      }
    }
    return new Fidelity(overlap / (double) exactResultCount,
        agreement / (double) queries.size());
  }

  /**
   * Validates collection elements and identifiers before embedding begins.
   *
   * @param passages The passage inputs.
   * @param dictionary The dictionary inputs.
   * @throws IllegalArgumentException Thrown if an element is {@code null} or an identifier
   *     repeats.
   */
  static void validateInputs(List<CasePassage> passages,
                             List<DictionaryEntry> dictionary) {
    final Set<String> passageIds = new HashSet<>(passages.size() * 2);
    for (int i = 0; i < passages.size(); i++) {
      final CasePassage passage = passages.get(i);
      if (passage == null) {
        throw new IllegalArgumentException("passages must not contain null at index " + i);
      }
      if (!passageIds.add(passage.id())) {
        throw new IllegalArgumentException("passage id must be unique: " + passage.id());
      }
    }
    final Set<String> headwords = new HashSet<>(dictionary.size() * 2);
    for (int i = 0; i < dictionary.size(); i++) {
      final DictionaryEntry entry = dictionary.get(i);
      if (entry == null) {
        throw new IllegalArgumentException("dictionary must not contain null at index " + i);
      }
      if (!headwords.add(entry.headword())) {
        throw new IllegalArgumentException("dictionary headword must be unique: "
            + entry.headword());
      }
    }
  }

  /**
   * Tests whether a model-produced embedding has a direction.
   *
   * @param vector The embedding vector.
   * @return {@code true} when at least one coordinate is nonzero.
   */
  static boolean hasDirection(float[] vector) {
    for (final float value : vector) {
      if (value != 0f) {
        return true;
      }
    }
    return false;
  }

  /**
   * Scores one retrieval evaluation: each query's target contributes its reciprocal rank when
   * it appears in the index's top {@code topK}, zero when it does not. Queries whose embedding
   * is all zero are dropped from the denominator and counted
   * out of {@code queries}.
   *
   * @param name    The index's display name.
   * @param index   The index to query.
   * @param queries The query vectors.
   * @param targets The relevant id of each query, aligned with {@code queries}.
   * @param topK    The evaluation depth.
   * @return The metrics.
   */
  static RetrievalMetrics retrieval(String name, VectorIndex index,
                                            List<float[]> queries, List<String> targets,
                                            int topK) {
    int usable = 0;
    double reciprocalRankSum = 0;
    int atOne = 0;
    int withinK = 0;
    for (int i = 0; i < queries.size(); i++) {
      final float[] query = queries.get(i);
      if (!hasDirection(query)) {
        continue;
      }
      usable++;
      final List<VectorIndex.Hit> hits = index.topK(query, topK);
      final String target = targets.get(i);
      for (int rank = 0; rank < hits.size(); rank++) {
        if (hits.get(rank).id().equals(target)) {
          reciprocalRankSum += 1.0 / (rank + 1);
          withinK++;
          if (rank == 0) {
            atOne++;
          }
          break;
        }
      }
    }
    final double denominator = Math.max(usable, 1);
    return new RetrievalMetrics(name, usable, reciprocalRankSum / denominator,
        atOne / denominator, withinK / denominator);
  }

  /**
   * Measures single-thread throughput: one timed pass of every query. The caller warms the
   * index (and the JIT) with an untimed pass first.
   *
   * @param index   The index to measure.
   * @param queries The query vectors.
   * @param topK    The result depth per query.
   * @return Queries per second.
   */
  static double queriesPerSecond(VectorIndex index, float[][] queries, int topK) {
    final long start = System.nanoTime();
    for (final float[] query : queries) {
      index.topK(query, topK);
    }
    final double seconds = (System.nanoTime() - start) / 1e9;
    return queries.length / Math.max(seconds, 1e-9);
  }

  /**
   * {@return the first half of a text, cut at the last space before the midpoint when there is
   * one} A passage's first half still describes the same case, so the passage itself is the
   * relevant answer for it.
   *
   * @param text The passage text.
   */
  static String firstHalf(String text) {
    final int midpoint = text.offsetByCodePoints(
        0, text.codePointCount(0, text.length()) / 2);
    if (midpoint == 0) {
      return text;
    }
    final int cut = text.lastIndexOf(' ', midpoint);
    return text.substring(0, cut > 0 ? cut : midpoint);
  }

  /**
   * {@return the elapsed milliseconds since a {@link System#nanoTime()} mark}
   *
   * @param startNanos The mark.
   */
  static long millisSince(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000;
  }

  /**
   * Validates an index or retrieval name.
   *
   * @param name The name.
   * @throws IllegalArgumentException Thrown if {@code name} is {@code null} or blank.
   */
  private static void requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be null or blank");
    }
    if (name.indexOf('\t') >= 0 || name.indexOf('\r') >= 0
        || name.indexOf('\n') >= 0 || name.indexOf('|') >= 0) {
      throw new IllegalArgumentException(
          "name must not contain a tab, carriage return, line feed, or pipe");
    }
  }

  /**
   * Validates a positive integer.
   *
   * @param value The value.
   * @param name The argument name.
   * @throws IllegalArgumentException Thrown if {@code value} is not positive.
   */
  private static void requirePositive(int value, String name) {
    if (value < 1) {
      throw new IllegalArgumentException(name + " must be at least 1: " + value);
    }
  }

  /**
   * Validates a nonnegative numeric value.
   *
   * @param value The value.
   * @param name The argument name.
   * @throws IllegalArgumentException Thrown if {@code value} is negative or non-finite.
   */
  private static void requireNonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0) {
      throw new IllegalArgumentException(name + " must be finite and nonnegative: " + value);
    }
  }

  /**
   * Validates an inclusive count range.
   *
   * @param value The subset count.
   * @param maximum The total count.
   * @param name The subset argument name.
   * @param maximumName The total argument name.
   * @throws IllegalArgumentException Thrown if {@code value} exceeds {@code maximum}.
   */
  private static void requireRange(int value, int maximum, String name, String maximumName) {
    if (value > maximum) {
      throw new IllegalArgumentException(name + " must not exceed " + maximumName + ": "
          + value + " > " + maximum);
    }
  }

  /**
   * Validates a ratio.
   *
   * @param value The ratio.
   * @param name The argument name.
   * @throws IllegalArgumentException Thrown if {@code value} is outside {@code [0, 1]}.
   */
  private static void requireRatio(double value, String name) {
    if (!Double.isFinite(value) || value < 0 || value > 1) {
      throw new IllegalArgumentException(name + " must be between 0 and 1: " + value);
    }
  }

  /**
   * Validates and copies a retrieval-metric list.
   *
   * @param metrics The metrics.
   * @param name The argument name.
   * @return The immutable copy.
   * @throws IllegalArgumentException Thrown if {@code metrics} is {@code null} or contains
   *     {@code null}.
   */
  private static List<RetrievalMetrics> copyMetrics(
      List<RetrievalMetrics> metrics, String name) {
    if (metrics == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
    for (int i = 0; i < metrics.size(); i++) {
      if (metrics.get(i) == null) {
        throw new IllegalArgumentException(name + " must not contain null at index " + i);
      }
    }
    return List.copyOf(metrics);
  }
}
