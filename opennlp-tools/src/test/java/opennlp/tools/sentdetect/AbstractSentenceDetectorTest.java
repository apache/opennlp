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

  protected static final Locale LOCALE_SPANISH = new Locale("es");
  protected static final Locale LOCALE_POLISH = new Locale("pl");
  protected static final Locale LOCALE_PORTUGUESE = new Locale("pt");

  static ObjectStream<SentenceSample> createSampleStream(Locale loc) throws IOException {
    final String trainingResource;
    if (loc.equals(Locale.GERMAN)) {
      trainingResource = "/opennlp/tools/sentdetect/Sentences_DE.txt";
    } else if (loc.equals(Locale.FRENCH)) {
      trainingResource = "/opennlp/tools/sentdetect/Sentences_FR.txt";
    } else if (loc.equals(Locale.ITALIAN)) {
      trainingResource = "/opennlp/tools/sentdetect/Sentences_IT.txt";
    } else if (loc.equals(LOCALE_POLISH)) {
      trainingResource = "/opennlp/tools/sentdetect/Sentences_PL.txt";
    } else if (loc.equals(LOCALE_PORTUGUESE)) {
      trainingResource = "/opennlp/tools/sentdetect/Sentences_PT.txt";
    } else if (loc.equals(LOCALE_SPANISH)) {
      trainingResource = "/opennlp/tools/sentdetect/Sentences_ES.txt";
    } else {
      trainingResource = "/opennlp/tools/sentdetect/Sentences.txt";
    }
    InputStreamFactory in = new ResourceAsStreamFactory(
            AbstractSentenceDetectorTest.class, trainingResource);
    return new SentenceSampleStream(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
  }

  static SentenceModel train(SentenceDetectorFactory factory, Locale loc) throws IOException {
    final String lang;
    if (loc.equals(Locale.GERMAN)) {
      lang = "deu";
    } else if (loc.equals(Locale.FRENCH)) {
      lang = "fra";
    } else if (loc.equals(Locale.ITALIAN)) {
      lang = "ita";
    } else if (loc.equals(LOCALE_POLISH)) {
      lang = "pol";
    } else if (loc.equals(LOCALE_PORTUGUESE)) {
      lang = "por";
    } else if (loc.equals(LOCALE_SPANISH)) {
      lang = "spa";
    } else {
      lang = "eng";
    }
    return SentenceDetectorME.train(lang, createSampleStream(loc), factory,
            TrainingParameters.defaultParams());
  }

  static Dictionary loadAbbDictionary(Locale loc) throws IOException {
    final String abbrevDict;
    if (loc.equals(Locale.GERMAN)) {
      abbrevDict = "opennlp/tools/lang/abb_DE.xml";
    } else if (loc.equals(Locale.FRENCH)) {
      abbrevDict = "opennlp/tools/lang/abb_FR.xml";
    } else if (loc.equals(Locale.ITALIAN)) {
      abbrevDict = "opennlp/tools/lang/abb_IT.xml";
    } else if (loc.equals(LOCALE_POLISH)) {
      abbrevDict = "opennlp/tools/lang/abb_PT.xml";
    } else if (loc.equals(LOCALE_PORTUGUESE)) {
      abbrevDict = "opennlp/tools/lang/abb_PT.xml";
    } else if (loc.equals(LOCALE_SPANISH)) {
      abbrevDict = "opennlp/tools/lang/abb_ES.xml";
    } else {
      abbrevDict = "opennlp/tools/lang/abb_EN.xml";
    }
    return new Dictionary(AbstractSentenceDetectorTest.class.getClassLoader()
            .getResourceAsStream(abbrevDict));
  }
}
