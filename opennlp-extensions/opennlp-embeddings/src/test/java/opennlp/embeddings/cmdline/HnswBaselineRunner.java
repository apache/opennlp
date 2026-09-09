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
package opennlp.embeddings.cmdline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import opennlp.embeddings.StaticEmbeddingModel;
import opennlp.embeddings.corpus.CasePassage;
import opennlp.embeddings.corpus.DictionaryEntry;
import opennlp.embeddings.eval.HnswBaseline;

/** Test-classpath command using the evaluation report file checks. */
public final class HnswBaselineRunner {

  /** Not instantiable. */
  private HnswBaselineRunner() {
  }

  /**
   * Runs {@code HnswBaseline model-dir passages-jsonl dictionary-tsv out-md [topK]}.
   *
   * @param args The command arguments.
   * @throws IOException If an input cannot be read or a report cannot be written.
   * @throws IllegalArgumentException If an evaluation argument or report path is invalid.
   */
  public static void main(String[] args) throws IOException {
    if (args.length < 4 || args.length > 5) {
      System.err.println(
          "Usage: HnswBaseline model-dir passages-jsonl dictionary-tsv out-md [topK]");
      System.exit(1);
    }
    final Path passagesFile = Path.of(args[1]);
    final Path dictionaryFile = Path.of(args[2]);
    final EvaluationReportFiles reports =
        new EvaluationReportFiles(Path.of(args[3]), passagesFile, dictionaryFile);
    final StaticEmbeddingModel model = StaticEmbeddingModel.load(Path.of(args[0]));
    final List<CasePassage> passages = CasePassage.readJsonl(passagesFile);
    final List<DictionaryEntry> dictionary = DictionaryEntry.readTsv(dictionaryFile);
    final int topK = args.length == 5 ? Integer.parseInt(args[4]) : 10;
    System.out.println("Evaluating " + passages.size() + " passages and " + dictionary.size()
        + " headwords against Lucene HNSW, top " + topK);
    final HnswBaseline.Report report = HnswBaseline.run(model, passages, dictionary, topK);
    reports.write(report.toMarkdown(), report.toTsv());
    System.out.println("Fidelity recall@" + topK + " " + report.fidelityRecallAtK()
        + ", exact QPS " + Math.round(report.exact().queriesPerSecond())
        + ", hnsw QPS " + Math.round(report.hnsw().queriesPerSecond()));
    System.out.println("Wrote " + reports);
  }
}
