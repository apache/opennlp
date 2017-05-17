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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.util.TrainingParameters;

public class LanguageDetectorCrossValidatorTest {

  @Test
  public void evaluate() throws Exception {

    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ITERATIONS_PARAM, 100);
    params.put(TrainingParameters.CUTOFF_PARAM, 5);
    params.put("PrintMessages", false);


    final AtomicInteger correctCount = new AtomicInteger();
    final AtomicInteger incorrectCount = new AtomicInteger();

    LanguageDetectorCrossValidator cv = new LanguageDetectorCrossValidator(params,
        new LanguageDetectorFactory(), new LanguageDetectorEvaluationMonitor() {
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
        });

    LanguageDetectorSampleStream sampleStream = LanguageDetectorMETest.createSampleStream();

    cv.evaluate(sampleStream, 2);

    Assert.assertEquals(99, cv.getDocumentCount());
    Assert.assertEquals(0.98989898989899, cv.getDocumentAccuracy(), 0.01);
  }

}
