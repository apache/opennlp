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

package opennlp.tools.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.parser.lang.en.HeadRules;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.FMeasure;

/**
 * Tests {@link ParserEvaluator}. Samples and test assumptions taken from historic "main" method,
 * that was once available in the class under test.
 * See: <a href="https://issues.apache.org/jira/projects/OPENNLP/issues/OPENNLP-689">OPENNLP-689</a>.
 */
public class ParserEvaluatorTest {

  private Parser parser;

  @BeforeEach
  public void setup() throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(getClass(),
            "/opennlp/tools/parser/parser.train");
    ParseSampleStream samples = new ParseSampleStream(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
    Assertions.assertNotNull(samples);

    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    try (InputStream headRulesIn = cl.getResourceAsStream("opennlp/tools/parser/en_head_rules")) {
      HeadRules headRules = new HeadRules(new BufferedReader(
              new InputStreamReader(headRulesIn, StandardCharsets.UTF_8)));

      ParserModel model = opennlp.tools.parser.chunking.Parser.train(
              "en", samples, headRules, TrainingParameters.defaultParams());
      parser = ParserFactory.create(model);
      Assertions.assertNotNull(parser);
    }
  }

  @Test
  void testProcessSample() {

    String goldParseString = "(TOP (S (NP (NNS Sales) (NNS executives)) (VP (VBD were) " +
            "(VP (VBG examing) (NP (DT the) (NNS figures)) (PP (IN with) (NP (JJ great) (NN care))) ))  " +
            "(NP (NN yesterday)) (. .) ))";
    ParserEvaluator pe = new ParserEvaluator(parser);
    Parse p = pe.processSample(Parse.parseParse(goldParseString));
    Assertions.assertNotNull(p);

    FMeasure measure = pe.getFMeasure();
    Assertions.assertNotNull(measure);

    // Expected output:  Precision: 0.42857142857142855, Recall: 0.375, F-Measure: 0.39999999999999997
    Assertions.assertEquals(measure.getPrecisionScore(), 0.42857142857142855d, 0d);
    Assertions.assertEquals(measure.getRecallScore(), 0.375d, 0d);
    Assertions.assertEquals(measure.getFMeasure(), 0.39999999999999997d, 0d);
  }

}
