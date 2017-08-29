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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.cmdline.langdetect.LanguageDetectorEvaluationErrorListener;


public class LanguageDetectorEvaluatorTest {

  @Test
  public void processSample() throws Exception {
    LanguageDetectorModel model = LanguageDetectorMETest.trainModel();
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
          public void missclassified(LanguageSample reference,
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


    Assert.assertEquals(1, correctCount.get());
    Assert.assertEquals(2, incorrectCount.get());

    Assert.assertEquals(3, evaluator.getDocumentCount());
    Assert.assertEquals(0.33, evaluator.getAccuracy(), 0.01);

    String report = outputStream.toString(StandardCharsets.UTF_8.name());

    Assert.assertEquals("Expected\tPredicted\tContext\n" +
        "fra\tpob\tescreve e faz palestras pelo mundo inteiro sobre anjos\n" +
        "fra\tpob\tescreve e faz palestras pelo mundo inteiro sobre anjos\n", report);
  }

}
