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

package opennlp.tools.langdetect;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.cmdline.langdetect.LanguageDetectorEvaluationErrorListener;
import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;


public class LanguageDetectorEvaluatorTest {

  static LanguageDetectorModel trainModel() throws Exception {
    return trainModel(new LanguageDetectorFactory());
  }

  private static LanguageDetectorModel trainModel(LanguageDetectorFactory factory) throws Exception {
    LanguageDetectorSampleStream sampleStream = createSampleStream();

    TrainingParameters params = new TrainingParameters();
    params.put(Parameters.ITERATIONS_PARAM, 100);
    params.put(Parameters.CUTOFF_PARAM, 5);
    params.put("DataIndexer", "TwoPass");
    params.put(Parameters.ALGORITHM_PARAM, "NAIVEBAYES");

    return LanguageDetectorME.train(sampleStream, params, factory);
  }

  private static LanguageDetectorSampleStream createSampleStream() throws IOException {

    ResourceAsStreamFactory streamFactory = new ResourceAsStreamFactory(
            LanguageDetectorEvaluatorTest.class, "/opennlp/tools/doccat/DoccatSample.txt");

    PlainTextByLineStream lineStream = new PlainTextByLineStream(streamFactory, StandardCharsets.UTF_8);

    return new LanguageDetectorSampleStream(lineStream);
  }

  @Test
  void processSample() throws Exception {
    LanguageDetectorModel model = trainModel();
    LanguageDetectorME langdetector = new LanguageDetectorME(model);

    final AtomicInteger correctCount = new AtomicInteger();
    final AtomicInteger incorrectCount = new AtomicInteger();

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    LanguageDetectorEvaluator evaluator = new LanguageDetectorEvaluator(langdetector,
        new LanguageDetectorEvaluationMonitor() {
          @Override
          public void correctlyClassified(LanguageSample reference,
                                          LanguageSample prediction) {
            correctCount.incrementAndGet();
          }

          @Override
          public void misclassified(LanguageSample reference,
                                    LanguageSample prediction) {
            incorrectCount.incrementAndGet();
          }
        }, new LanguageDetectorEvaluationErrorListener(outputStream));

    evaluator.evaluateSample(new LanguageSample(new Language("pob"),
        "escreve e faz palestras pelo mundo inteiro sobre anjos"));

    evaluator.evaluateSample(new LanguageSample(new Language("fra"),
        "escreve e faz palestras pelo mundo inteiro sobre anjos"));

    evaluator.evaluateSample(new LanguageSample(new Language("fra"),
        "escreve e faz palestras pelo mundo inteiro sobre anjos"));


    Assertions.assertEquals(1, correctCount.get());
    Assertions.assertEquals(2, incorrectCount.get());

    Assertions.assertEquals(3, evaluator.getDocumentCount());
    Assertions.assertEquals(0.01, evaluator.getAccuracy(), 0.33);

    String report = outputStream.toString(StandardCharsets.UTF_8);

    Assertions.assertEquals("Expected\tPredicted\tContext" + System.lineSeparator() +
        "fra\tpob\tescreve e faz palestras pelo mundo inteiro sobre anjos" + System.lineSeparator() +
        "fra\tpob\tescreve e faz palestras pelo mundo inteiro sobre anjos" + System.lineSeparator(), report);
  }

}
