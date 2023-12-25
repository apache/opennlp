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


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

public class LanguageDetectorFactoryTest {

  private static LanguageDetectorModel model;

  private static byte[] serialized;

  @BeforeAll
  static void train() throws Exception {

    ResourceAsStreamFactory streamFactory = new ResourceAsStreamFactory(
        LanguageDetectorMETest.class, "/opennlp/tools/doccat/DoccatSample.txt");

    PlainTextByLineStream lineStream = new PlainTextByLineStream(streamFactory, StandardCharsets.UTF_8);

    LanguageDetectorSampleStream sampleStream = new LanguageDetectorSampleStream(lineStream);

    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ITERATIONS_PARAM, "100");
    params.put(TrainingParameters.CUTOFF_PARAM, "5");
    params.put(TrainingParameters.ALGORITHM_PARAM, "NAIVEBAYES");

    model = LanguageDetectorME.train(sampleStream, params, new DummyFactory());
    serialized = LanguageDetectorMETest.serializeModel(model);
  }

  @Test
  void testCorrectFactory() throws IOException {
    LanguageDetectorModel myModel = new LanguageDetectorModel(new ByteArrayInputStream(serialized));
    Assertions.assertNotNull(myModel.getFactory());
    Assertions.assertInstanceOf(LanguageDetectorFactory.class, myModel.getFactory());
  }

  @Test
  void testDummyFactory() throws IOException {
    LanguageDetectorModel myModel = new LanguageDetectorModel(new ByteArrayInputStream(serialized));
    Assertions.assertNotNull(myModel.getFactory());
    Assertions.assertInstanceOf(DummyFactory.class, myModel.getFactory());
  }

  @Test
  void testDummyFactoryContextGenerator() {
    LanguageDetectorContextGenerator cg = model.getFactory().getContextGenerator();
    String[] context = cg.getContext(
        "a dummy text phrase to test if the context generator works!!!!!!!!!!!!");

    Set<String> set = new HashSet<>(Arrays.asList(context));

    Assertions.assertTrue(set.contains("!!!!!")); // default normalizer would remove the repeated !
    Assertions.assertTrue(set.contains("a dum"));
    Assertions.assertTrue(set.contains("tg=[THE,CONTEXT,GENERATOR]"));
  }

}
