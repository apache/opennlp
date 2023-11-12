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

package opennlp.tools.sentdetect;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

public abstract class AbstractSentenceDetectorTest {
  
  static ObjectStream<SentenceSample> createSampleStream(Locale loc) throws IOException {
    final String trainingResource;
    if (loc.equals(Locale.GERMAN)) {
      trainingResource = "/opennlp/tools/sentdetect/Sentences_DE.txt";
    } else {
      trainingResource = "/opennlp/tools/sentdetect/Sentences.txt";
    }
    InputStreamFactory in = new ResourceAsStreamFactory(
            AbstractSentenceDetectorTest.class, trainingResource);
    return new SentenceSampleStream(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
  }

  static SentenceModel train(SentenceDetectorFactory factory, Locale loc) throws IOException {
    final String languageCode;
    if (loc.equals(Locale.GERMAN)) {
      languageCode = "deu";
    } else {
      languageCode = "eng";
    }
    return SentenceDetectorME.train(languageCode, createSampleStream(loc), factory,
            TrainingParameters.defaultParams());
  }

  static Dictionary loadAbbDictionary(Locale loc) throws IOException {
    final String abbrevDict;
    if (loc.equals(Locale.GERMAN)) {
      abbrevDict = "opennlp/tools/sentdetect/abb_DE.xml";
    } else {
      abbrevDict = "opennlp/tools/sentdetect/abb.xml";
    }
    return new Dictionary(AbstractSentenceDetectorTest.class.getClassLoader()
            .getResourceAsStream(abbrevDict));
  }
}
