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

package opennlp.tools.namefind;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.cmdline.namefind.NameEvaluationErrorListener;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringList;
import opennlp.tools.util.eval.FMeasure;

import org.junit.Test;

/**
 * Tests the evaluation of a {@link DictionaryNameFinder}.
 */
public class DictionaryNameFinderEvaluatorTest {

  @Test
  public void testEvaluator() throws IOException, URISyntaxException {
    DictionaryNameFinder nameFinder = new DictionaryNameFinder(
        createDictionary());
    TokenNameFinderEvaluator evaluator = new TokenNameFinderEvaluator(
        nameFinder, new NameEvaluationErrorListener());
    ObjectStream<NameSample> sample = createSample();

    evaluator.evaluate(sample);
    sample.close();
    FMeasure fmeasure = evaluator.getFMeasure();

    assertTrue(fmeasure.getFMeasure() == 1);
    assertTrue(fmeasure.getRecallScore() == 1);
  }

  /**
   * Creates a NameSample stream using an annotated corpus
   * 
   * @return
   * @throws IOException
   * @throws URISyntaxException
   */
  private static ObjectStream<NameSample> createSample() throws IOException,
      URISyntaxException {
    FileInputStream sampleDataIn = new FileInputStream(new File(
        DictionaryNameFinderEvaluatorTest.class.getClassLoader()
            .getResource("opennlp/tools/namefind/AnnotatedSentences.txt")
            .toURI()));

    return new NameSampleDataStream(new PlainTextByLineStream(
        sampleDataIn.getChannel(), "ISO-8859-1"));
  }

  /**
   * Creates a dictionary with all names from the sample data.
   * 
   * @return a dictionary
   * @throws IOException
   * @throws URISyntaxException
   */
  private static Dictionary createDictionary() throws IOException,
      URISyntaxException {
    ObjectStream<NameSample> sampleStream = createSample();
    NameSample sample = sampleStream.read();
    List<String[]> entries = new ArrayList<String[]>();
    while (sample != null) {
      Span[] names = sample.getNames();
      if (names != null && names.length > 0) {
        String[] toks = sample.getSentence();
        for (Span name : names) {
          String[] nameToks = new String[name.length()];
          System.arraycopy(toks, name.getStart(), nameToks, 0, name.length());
          entries.add(nameToks);
        }
      }
      sample = sampleStream.read();
    }
    sampleStream.close();
    Dictionary dictionary = new Dictionary(true);
    for (String[] entry : entries) {
      StringList dicEntry = new StringList(entry);
      dictionary.put(dicEntry);
    }
    return dictionary;
  }
}
