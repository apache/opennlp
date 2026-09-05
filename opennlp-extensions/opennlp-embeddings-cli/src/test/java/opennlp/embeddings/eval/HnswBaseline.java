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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import opennlp.embeddings.StaticEmbeddingModel;
import opennlp.embeddings.corpus.CasePassage;
import opennlp.embeddings.corpus.DictionaryEntry;
import opennlp.embeddings.eval.SearchEvaluator.IndexMetrics;
import opennlp.embeddings.eval.SearchEvaluator.RetrievalMetrics;
import opennlp.embeddings.index.FlatFloatIndex;
import opennlp.embeddings.index.HnswFloatIndex;

/**
 * Runs the Lucene HNSW index through the same measurements as
 * {@link SearchEvaluator#run(StaticEmbeddingModel, List, List, int, long, int)}: index the same
 * indexable passages, measure fidelity against the exact scan, and run both retrieval
 * evaluations. This test utility does not add Lucene to the runtime dependencies.
 */
public final class HnswBaseline {

  private static final String INDEX_NAME = "hnsw";

  /** Not instantiable. */
  private HnswBaseline() {
  }

  /**
   * One baseline run.
   *
   * @param passageCount         The total number of supplied passages.
   * @param indexedPassageCount  The number of passages with a usable embedding.
   * @param headwordCount        The total number of supplied dictionary headwords.
   * @param indexedHeadwordCount The number of headwords with a usable embedding.
   * @param dimension            The embedding dimension.
   * @param topK                 The evaluated depth.
   * @param exact                The exact passage index's build and throughput metrics.
   * @param hnsw                 The HNSW passage index's build and throughput metrics.
   * @param fidelityRecallAtK    The HNSW index's mean overlap with the exact top-k.
   * @param fidelityAgreement    The share of queries where both indexes rank the same id first.
   * @param definitionToHeadword The definition-to-headword metrics on the HNSW headword index.
   * @param halfPassage          The half-passage metrics on the HNSW passage index.
   */
  public record Report(int passageCount, int indexedPassageCount,
                       int headwordCount, int indexedHeadwordCount,
                       int dimension, int topK,
                       IndexMetrics exact, IndexMetrics hnsw,
                       double fidelityRecallAtK, double fidelityAgreement,
                       RetrievalMetrics definitionToHeadword, RetrievalMetrics halfPassage) {

    /**
     * Validates one baseline report.
     *
     * @throws IllegalArgumentException Thrown if a value is invalid or missing.
     */
    public Report {
      if (passageCount < 1) {
        throw new IllegalArgumentException("passageCount must be at least 1: " + passageCount);
      }
      if (indexedPassageCount < 1 || indexedPassageCount > passageCount) {
        throw new IllegalArgumentException("indexedPassageCount must be in [1, passageCount]: "
            + indexedPassageCount);
      }
      if (headwordCount < 0) {
        throw new IllegalArgumentException("headwordCount must be nonnegative: " + headwordCount);
      }
      if (indexedHeadwordCount < 0 || indexedHeadwordCount > headwordCount) {
        throw new IllegalArgumentException(
            "indexedHeadwordCount must be in [0, headwordCount]: " + indexedHeadwordCount);
      }
      if (dimension < 1) {
        throw new IllegalArgumentException("dimension must be at least 1: " + dimension);
      }
      if (topK < 1) {
        throw new IllegalArgumentException("topK must be at least 1: " + topK);
      }
      if (exact == null || hnsw == null || halfPassage == null) {
        throw new IllegalArgumentException("exact, hnsw, and halfPassage must not be null");
      }
      if (exact.rows() != indexedPassageCount || hnsw.rows() != indexedPassageCount) {
        throw new IllegalArgumentException(
            "index row counts must equal indexedPassageCount: " + indexedPassageCount);
      }
      if ((headwordCount == 0) != (definitionToHeadword == null)) {
        throw new IllegalArgumentException(
            "definitionToHeadword must be null exactly when headwordCount is zero");
      }
      if (definitionToHeadword != null
          && definitionToHeadword.queries() > indexedHeadwordCount) {
        throw new IllegalArgumentException(
            "definition query count must not exceed indexedHeadwordCount: "
                + definitionToHeadword.queries() + " > " + indexedHeadwordCount);
      }
      if (halfPassage.queries() > indexedPassageCount) {
        throw new IllegalArgumentException(
            "half-passage query count must not exceed indexedPassageCount: "
                + halfPassage.queries() + " > " + indexedPassageCount);
      }
      if (!Double.isFinite(fidelityRecallAtK) || fidelityRecallAtK < 0
          || fidelityRecallAtK > 1) {
        throw new IllegalArgumentException("fidelityRecallAtK must be between 0 and 1: "
            + fidelityRecallAtK);
      }
      if (!Double.isFinite(fidelityAgreement) || fidelityAgreement < 0
          || fidelityAgreement > 1) {
        throw new IllegalArgumentException("fidelityAgreement must be between 0 and 1: "
            + fidelityAgreement);
      }
    }

    /** {@return the human-readable report as GitHub-flavored markdown} */
    public String toMarkdown() {
      final StringBuilder md = new StringBuilder(2048);
      md.append("# HNSW baseline\n\n");
      md.append("Lucene HNSW (default graph parameters, ")
          .append(HnswFloatIndex.DEFAULT_SEARCH_WIDTH)
          .append("-candidate search beam) over the same embedded corpus: ")
          .append(passageCount).append(" passages (").append(indexedPassageCount)
          .append(" indexable), ").append(headwordCount).append(" dictionary headwords (")
          .append(indexedHeadwordCount).append(" indexable), dimension ").append(dimension)
          .append(". Evaluation depth: top ").append(topK).append(".\n\n");

      md.append("## Passage index build and throughput\n\n");
      md.append("| index | rows | storage bytes/vector | build (ms) | QPS (1 thread) |\n");
      md.append("|---|---|---|---|---|\n");
      for (final IndexMetrics index : List.of(exact, hnsw)) {
        md.append("| ").append(index.name())
            .append(" | ").append(index.rows())
            .append(" | ").append(format(index.storageBytesPerVector()))
            .append(" | ").append(index.buildMillis())
            .append(" | ").append(String.format(Locale.ROOT, "%.0f", index.queriesPerSecond()))
            .append(" |\n");
      }
      md.append('\n');

      md.append("## Index fidelity (hnsw vs exact, passages as queries)\n\n");
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
     * Appends one retrieval section, or notes the evaluation was skipped.
     *
     * @param md      The markdown under construction.
     * @param title   The section title.
     * @param metrics The metrics, or {@code null} when the evaluation had no inputs.
     */
    private void appendRetrieval(StringBuilder md, String title, RetrievalMetrics metrics) {
      md.append("## ").append(title).append("\n\n");
      if (metrics == null) {
        md.append("Skipped: no dictionary entries were supplied.\n\n");
        return;
      }
      md.append("| index | queries | MRR@").append(topK)
          .append(" | recall@1 | recall@").append(topK).append(" |\n");
      md.append("|---|---|---|---|---|\n");
      md.append("| ").append(metrics.name())
          .append(" | ").append(metrics.queries())
          .append(" | ").append(format(metrics.mrr()))
          .append(" | ").append(format(metrics.recallAt1()))
          .append(" | ").append(format(metrics.recallAtK()))
          .append(" |\n\n");
    }

    /** {@return the machine-readable report: one {@code key<TAB>value} line per metric} */
    public String toTsv() {
      final StringBuilder tsv = new StringBuilder(512);
      line(tsv, "passages", passageCount);
      line(tsv, "passages.indexed", indexedPassageCount);
      line(tsv, "headwords", headwordCount);
      line(tsv, "headwords.indexed", indexedHeadwordCount);
      line(tsv, "dimension", dimension);
      line(tsv, "topK", topK);
      for (final IndexMetrics index : List.of(exact, hnsw)) {
        line(tsv, index.name() + ".storageBytesPerVector",
            format(index.storageBytesPerVector()));
        line(tsv, index.name() + ".build.millis", index.buildMillis());
        line(tsv, index.name() + ".qps", String.format(Locale.ROOT, "%.0f",
            index.queriesPerSecond()));
      }
      line(tsv, "fidelity.recallAtK", format(fidelityRecallAtK));
      line(tsv, "fidelity.rank1Agreement", format(fidelityAgreement));
      line(tsv, "fidelity.queries", indexedPassageCount);
      if (definitionToHeadword != null) {
        retrievalLines(tsv, "definitionToHeadword." + definitionToHeadword.name(),
            definitionToHeadword);
      }
      retrievalLines(tsv, "halfPassage." + halfPassage.name(), halfPassage);
      return tsv.toString();
    }

    /**
     * Appends the four metric lines of one retrieval outcome.
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
   * Runs the baseline: embed, build the exact and HNSW passage indexes, measure fidelity and
   * throughput, and run both retrieval evaluations.
   *
   * @param model      The embedding model. Must not be {@code null}.
   * @param passages   The passages to index and query. Must not be {@code null} or empty; at
   *                   least one passage text must produce a usable embedding.
   * @param dictionary The dictionary entries for the definition-to-headword evaluation. Must
   *                   not be {@code null}; it may be empty, which skips that evaluation.
   * @param topK       The evaluation depth. Must be at least 1.
   * @return The report.
   * @throws IllegalArgumentException Thrown if an argument violates the constraints above.
   */
  public static Report run(StaticEmbeddingModel model, List<CasePassage> passages,
                           List<DictionaryEntry> dictionary, int topK) {
    if (model == null) {
      throw new IllegalArgumentException("model must not be null");
    }
    if (passages == null || passages.isEmpty()) {
      throw new IllegalArgumentException("passages must not be null or empty");
    }
    if (dictionary == null) {
      throw new IllegalArgumentException("dictionary must not be null");
    }
    if (topK < 1) {
      throw new IllegalArgumentException("topK must be at least 1: " + topK);
    }
    SearchEvaluator.validateInputs(passages, dictionary);

    final List<CasePassage> indexed = new ArrayList<>(passages.size());
    final List<float[]> vectors = new ArrayList<>(passages.size());
    for (final CasePassage passage : passages) {
      final float[] vector = model.embed(passage.text());
      if (SearchEvaluator.hasDirection(vector)) {
        indexed.add(passage);
        vectors.add(vector);
      }
    }
    if (indexed.isEmpty()) {
      throw new IllegalArgumentException(
          "passages must contain at least one text with a usable embedding");
    }

    final FlatFloatIndex exact = new FlatFloatIndex(model.dimension());
    try (HnswFloatIndex hnsw = new HnswFloatIndex(model.dimension())) {
      for (int i = 0; i < indexed.size(); i++) {
        exact.add(indexed.get(i).id(), vectors.get(i));
        hnsw.add(indexed.get(i).id(), vectors.get(i));
      }
      final long exactBuildStart = System.nanoTime();
      exact.freeze();
      final long exactBuildMillis = SearchEvaluator.millisSince(exactBuildStart);
      final long hnswBuildStart = System.nanoTime();
      hnsw.freeze();
      final long hnswBuildMillis = SearchEvaluator.millisSince(hnswBuildStart);

      // Fidelity doubles as the warm-up pass for the throughput measurement after it.
      final SearchEvaluator.Fidelity fidelity =
          SearchEvaluator.fidelity(exact, hnsw, vectors, topK);
      final float[][] timedQueries = vectors.toArray(float[][]::new);
      final IndexMetrics exactMetrics = new IndexMetrics("exact", exact.size(),
          model.dimension() * (double) Float.BYTES, exactBuildMillis,
          SearchEvaluator.queriesPerSecond(exact, timedQueries, topK));
      final IndexMetrics hnswMetrics = new IndexMetrics(INDEX_NAME, hnsw.size(),
          hnsw.serializedBytesPerVector(), hnswBuildMillis,
          SearchEvaluator.queriesPerSecond(hnsw, timedQueries, topK));

      RetrievalMetrics definitionToHeadword = null;
      int indexedHeadwordCount = 0;
      if (!dictionary.isEmpty()) {
        try (HnswFloatIndex headwords = new HnswFloatIndex(model.dimension())) {
          final List<String> targets = new ArrayList<>(dictionary.size());
          final List<float[]> queries = new ArrayList<>(dictionary.size());
          for (final DictionaryEntry entry : dictionary) {
            final float[] vector = model.embed(entry.headword());
            if (!SearchEvaluator.hasDirection(vector)) {
              continue;
            }
            headwords.add(entry.headword(), vector);
            targets.add(entry.headword());
            queries.add(model.embed(entry.definition()));
          }
          headwords.freeze();
          indexedHeadwordCount = targets.size();
          definitionToHeadword =
              SearchEvaluator.retrieval(INDEX_NAME, headwords, queries, targets, topK);
        }
      }

      final List<String> passageTargets = new ArrayList<>(indexed.size());
      final List<float[]> halfQueries = new ArrayList<>(indexed.size());
      for (final CasePassage passage : indexed) {
        halfQueries.add(model.embed(SearchEvaluator.firstHalf(passage.text())));
        passageTargets.add(passage.id());
      }
      final RetrievalMetrics halfPassage =
          SearchEvaluator.retrieval(INDEX_NAME, hnsw, halfQueries, passageTargets, topK);

      return new Report(passages.size(), indexed.size(), dictionary.size(),
          indexedHeadwordCount, model.dimension(), topK, exactMetrics, hnswMetrics,
          fidelity.recallAtK(), fidelity.rank1Agreement(), definitionToHeadword, halfPassage);
    }
  }

  /**
   * Runs the baseline from the command line, on the test classpath:
   * {@code HnswBaseline model-dir passages-jsonl dictionary-tsv out-md [topK]}. Writes the
   * markdown report to the given path and its TSV twin next to it.
   *
   * @param args The arguments above.
   * @throws IOException Thrown if an input cannot be read or a report cannot be written.
   */
  public static void main(String[] args) throws IOException {
    if (args.length < 4 || args.length > 5) {
      System.err.println(
          "Usage: HnswBaseline model-dir passages-jsonl dictionary-tsv out-md [topK]");
      System.exit(1);
    }
    final StaticEmbeddingModel model = StaticEmbeddingModel.load(Path.of(args[0]));
    final List<CasePassage> passages = CasePassage.readJsonl(Path.of(args[1]));
    final List<DictionaryEntry> dictionary = DictionaryEntry.readTsv(Path.of(args[2]));
    final Path out = Path.of(args[3]);
    final int topK = args.length == 5 ? Integer.parseInt(args[4]) : 10;
    System.out.println("Evaluating " + passages.size() + " passages and " + dictionary.size()
        + " headwords against Lucene HNSW, top " + topK);
    final Report report = run(model, passages, dictionary, topK);
    Files.writeString(out, report.toMarkdown());
    final String name = out.getFileName().toString();
    final int dot = name.lastIndexOf('.');
    final Path tsv = out.resolveSibling((dot > 0 ? name.substring(0, dot) : name) + ".tsv");
    Files.writeString(tsv, report.toTsv());
    System.out.println("Fidelity recall@" + topK + " " + report.fidelityRecallAtK()
        + ", exact QPS " + Math.round(report.exact().queriesPerSecond())
        + ", hnsw QPS " + Math.round(report.hnsw().queriesPerSecond()));
    System.out.println("Wrote " + out + " and " + tsv);
  }

}
