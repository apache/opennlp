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
package opennlp.tools.models;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import opennlp.tools.langdetect.LanguageDetector;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class AbstractModelUsageTest extends AbstractClassPathModelTest {

  @Test
  public void testLanguageDetection() throws IOException {
    final ClassPathModel model = getClassPathModel("opennlp-models-langdetect-*.jar");
    final LanguageDetectorModel ldModel = new LanguageDetectorModel(new ByteArrayInputStream(model.model()));
    assertNotNull(ldModel);
    final LanguageDetector languageDetector = new LanguageDetectorME(ldModel);
    assertNotNull(languageDetector);

    assertEquals("eng",
        languageDetector.predictLanguage("The English language is pretty impressive.").getLang());

  }

  @Test
  public void testSentenceDetector() throws IOException {
    final ClassPathModel model = getClassPathModel("opennlp-models-sentdetect-*.jar");
    final SentenceModel sentenceModel = new SentenceModel(new ByteArrayInputStream(model.model()));
    assertNotNull(sentenceModel);
    final SentenceDetector sentenceDetector = new SentenceDetectorME(sentenceModel);
    assertNotNull(sentenceDetector);

    assertEquals(2, sentenceDetector.sentDetect("Pretty impressive stuff. I like it!").length);

  }

}
