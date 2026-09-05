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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import opennlp.embeddings.StaticEmbeddingModel;
import opennlp.embeddings.corpus.CasePassage;
import opennlp.embeddings.corpus.DictionaryEntry;
import opennlp.embeddings.eval.SearchEvaluator;
import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.util.InvalidFormatException;

/**
 * Evaluates exact and quantized vector search over a passage corpus and writes markdown and TSV
 * reports. See {@link SearchEvaluator} for the metrics.
 */
public class EvalVectorSearchTool extends BasicCmdLineTool {

  interface Params extends EvalVectorSearchParams {
  }

  /** {@inheritDoc} */
  @Override
  public String getShortDescription() {
    return "Evaluates exact and quantized vector search over an embedded passage corpus";
  }

  /** {@inheritDoc} */
  @Override
  public String getHelp() {
    return getBasicHelp(Params.class);
  }

  /** {@inheritDoc} */
  @Override
  public void run(String[] args) {
    final Params params = validateAndParseParams(args, Params.class);
    final SearchEvaluator.Report report;
    final Path out = Path.of(params.getOut());
    try {
      final StaticEmbeddingModel model = StaticEmbeddingModel.load(Path.of(params.getModel()));
      final List<CasePassage> passages = CasePassage.readJsonl(Path.of(params.getPassages()));
      final List<DictionaryEntry> dictionary =
          DictionaryEntry.readTsv(Path.of(params.getDictionary()));
      System.out.println("Evaluating " + passages.size() + " passages and "
          + dictionary.size() + " headwords at " + params.getBits() + " bits, top "
          + params.getTopK());
      report = SearchEvaluator.run(model, passages, dictionary, params.getBits(),
          params.getSeed(), params.getTopK());
      Files.writeString(out, report.toMarkdown());
      Files.writeString(tsvPath(out), report.toTsv());
    } catch (IllegalArgumentException | InvalidFormatException e) {
      throw new TerminateToolException(1, e.getMessage(), e);
    } catch (IOException e) {
      throw new TerminateToolException(-1,
          "IO error while evaluating: " + e.getMessage(), e);
    }
    System.out.println("Fidelity recall@" + report.topK() + " "
        + report.fidelityRecallAtK() + ", exact QPS "
        + Math.round(report.flat().queriesPerSecond()) + ", quantized QPS "
        + Math.round(report.quantized().queriesPerSecond()));
    System.out.println("Wrote " + out + " and " + tsvPath(out));
  }

  /**
   * {@return the TSV twin of the markdown report path, its extension replaced by {@code .tsv}}
   *
   * @param out The markdown report path.
   */
  private Path tsvPath(Path out) {
    final String name = out.getFileName().toString();
    final int dot = name.lastIndexOf('.');
    final String tsvName = (dot > 0 ? name.substring(0, dot) : name) + ".tsv";
    return out.resolveSibling(tsvName);
  }
}
