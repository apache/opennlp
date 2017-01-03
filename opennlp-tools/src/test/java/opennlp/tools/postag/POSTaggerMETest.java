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


package opennlp.tools.postag;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import opennlp.tools.util.TrainingParameters;
import org.junit.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.model.ModelType;

/**
 * Tests for the {@link POSTaggerME} class.
 */
public class POSTaggerMETest {

  private static ObjectStream<POSSample> createSampleStream() throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(POSTaggerMETest.class,
        "/opennlp/tools/postag/AnnotatedSentences.txt");

    return new WordTagSampleStream(new PlainTextByLineStream(in, UTF_8));
  }

  /**
   * Trains a POSModel from the annotated test data.
   *
   * @return
   * @throws IOException
   */
  static POSModel trainPOSModel(ModelType type) throws IOException {
    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ALGORITHM_PARAM, type.toString());
    params.put(TrainingParameters.ITERATIONS_PARAM, Integer.toString(100));
    params.put(TrainingParameters.CUTOFF_PARAM, Integer.toString(5));

    return POSTaggerME.train("en", createSampleStream(), params, new POSTaggerFactory());
  }

  @Test
  public void testPOSTagger() throws IOException {
    POSModel posModel = trainPOSModel(ModelType.MAXENT);

    POSTagger tagger = new POSTaggerME(posModel);

    String tags[] = tagger.tag(new String[] {
        "The",
        "driver",
        "got",
        "badly",
        "injured",
        "."});

    assertEquals(6, tags.length);

    assertEquals("DT", tags[0]);
    assertEquals("NN", tags[1]);
    assertEquals("VBD", tags[2]);
    assertEquals("RB", tags[3]);
    assertEquals("VBN", tags[4]);
    assertEquals(".", tags[5]);
  }

  @Test
  public void testBuildNGramDictionary() throws IOException {
    ObjectStream<POSSample> samples = createSampleStream();

    POSTaggerME.buildNGramDictionary(samples, 0);
  }
}
