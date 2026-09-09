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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import opennlp.embeddings.ModelDistiller;
import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.util.InvalidFormatException;

/**
 * Distills a local or Hugging Face sentence-transformer into a static embedding model. The
 * pipeline applies the teacher model, PCA, and Zipf weighting through {@link ModelDistiller}.
 */
public class DistillModelTool extends BasicCmdLineTool {

  /** Command-line parameters accepted by this tool. */
  interface Params extends DistillModelParams {
  }

  /** {@inheritDoc} */
  @Override
  public String getShortDescription() {
    return "Distills a sentence-transformer teacher into a static embedding model";
  }

  /** {@inheritDoc} */
  @Override
  public String getHelp() {
    return getBasicHelp(Params.class);
  }

  /** {@inheritDoc} */
  @Override
  public void run(String[] args) {
    // -teacher and -out are mandatory parameters, so validateAndParseParams has already
    // rejected the invocation if either is absent.
    final Params params = validateAndParseParams(args, Params.class);
    final ModelDistiller.ProgressListener listener = System.out::println;
    final ModelDistiller.Result result;
    try {
      final List<String> terms = params.getTerms() == null
          ? List.of() : readTerms(Path.of(params.getTerms()));
      result = ModelDistiller.distill(params.getTeacher(), Path.of(params.getOut()),
          params.getPcaDims(), terms, listener);
    } catch (IllegalArgumentException | InvalidFormatException e) {
      throw new TerminateToolException(1, e.getMessage(), e);
    } catch (IOException e) {
      throw new TerminateToolException(-1,
          "IO error while distilling: " + e.getMessage(), e);
    }
    System.out.println("Distilled and verified a " + result.family() + " model: "
        + result.vocabularySize() + " rows"
        + (result.termCount() > 0 ? " plus " + result.termCount() + " terms" : "")
        + ", " + result.teacherDimension() + "d -> "
        + result.dimension() + "d, PCA kept "
        + formatPercentage(result.explainedVarianceRatio()) + "% of the variance");
  }

  /**
   * Formats a variance ratio as a percentage with one decimal place.
   *
   * @param ratio The variance ratio.
   * @return The percentage using a decimal point independently of the default locale.
   */
  private String formatPercentage(double ratio) {
    return BigDecimal.valueOf(ratio).movePointRight(2)
        .setScale(1, RoundingMode.HALF_UP).toPlainString();
  }

  /**
   * Reads a term file: one term per line, text after the first tab ignored, blank lines
   * skipped. A learned vocabulary TSV (term, count, source) therefore works unchanged.
   *
   * @param file The term file.
   * @return The terms in file order.
   * @throws IOException Thrown if reading the file fails.
   */
  private List<String> readTerms(Path file) throws IOException {
    final List<String> terms = new ArrayList<>();
    for (final String line : Files.readAllLines(file)) {
      final int tab = line.indexOf('\t');
      final String term = (tab < 0 ? line : line.substring(0, tab)).strip();
      if (!term.isEmpty()) {
        terms.add(term);
      }
    }
    return terms;
  }
}
