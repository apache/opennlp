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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

public class LanguageDetectorFactoryTest {


  private static LanguageDetectorModel model;

  @BeforeClass
  public static void train() throws Exception {

    ResourceAsStreamFactory streamFactory = new ResourceAsStreamFactory(
        LanguageDetectorMETest.class, "/opennlp/tools/doccat/DoccatSample.txt");

    PlainTextByLineStream lineStream = new PlainTextByLineStream(streamFactory, "UTF-8");

    LanguageDetectorSampleStream sampleStream = new LanguageDetectorSampleStream(lineStream);

    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ITERATIONS_PARAM, "100");
    params.put(TrainingParameters.CUTOFF_PARAM, "5");
    params.put(TrainingParameters.ALGORITHM_PARAM, "NAIVEBAYES");

    model = LanguageDetectorME.train(sampleStream, params, new DummyFactory());
  }

  @Test
  public void testCorrectFactory() throws IOException {
    byte[] serialized = LanguageDetectorMETest.serializeModel(model);

    LanguageDetectorModel myModel = new LanguageDetectorModel(new ByteArrayInputStream(serialized));

    Assert.assertTrue(myModel.getFactory() instanceof DummyFactory);

  }

  @Test
  public void testDummyFactory() throws Exception {
    byte[] serialized = LanguageDetectorMETest.serializeModel(model);

    LanguageDetectorModel myModel = new LanguageDetectorModel(new ByteArrayInputStream(serialized));

    Assert.assertTrue(myModel.getFactory() instanceof DummyFactory);
  }

  @Test
  public void testDummyFactoryContextGenerator() throws Exception {
    LanguageDetectorContextGenerator cg = model.getFactory().getContextGenerator();
    String[] context = cg.getContext(
        "a dummy text phrase to test if the context generator works!!!!!!!!!!!!");

    Set<String> set = new HashSet(Arrays.asList(context));

    Assert.assertTrue(set.contains("!!!!!")); // default normalizer would remove the repeated !
    Assert.assertTrue(set.contains("a dum"));
    Assert.assertTrue(set.contains("tg=[THE,CONTEXT,GENERATOR]"));
  }

}
