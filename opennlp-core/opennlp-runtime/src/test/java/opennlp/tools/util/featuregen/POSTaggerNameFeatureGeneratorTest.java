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

package opennlp.tools.util.featuregen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerFactory;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.postag.WordTagSampleStream;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelType;

public class POSTaggerNameFeatureGeneratorTest {

  private static ObjectStream<POSSample> createSampleStream() throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(POSTaggerNameFeatureGeneratorTest.class,
            "/opennlp/tools/postag/AnnotatedSentences.txt"); //PENN FORMAT

    return new WordTagSampleStream(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
  }

  /**
   * Trains a POSModel from the annotated test data.
   *
   * @return {@link POSModel}
   */
  static POSModel trainPennFormatPOSModel(ModelType type) throws IOException {
    TrainingParameters params = new TrainingParameters();
    params.put(Parameters.ALGORITHM_PARAM, type.toString());
    params.put(Parameters.ITERATIONS_PARAM, 100);
    params.put(Parameters.CUTOFF_PARAM, 5);

    return POSTaggerME.train("eng", createSampleStream(), params,
            new POSTaggerFactory());
  }

  @Test
  void testFeatureGeneration() throws IOException {
    POSTaggerNameFeatureGenerator fg = new POSTaggerNameFeatureGenerator(
            trainPennFormatPOSModel(ModelType.MAXENT));

    String[] tokens = {"Hi", "Mike", ",", "it", "'s", "Stefanie", "Schmidt", "."};
    for (int i = 0; i < tokens.length; i++) {
      List<String> feats = new ArrayList<>();
      fg.createFeatures(feats, tokens, i, null);
      Assertions.assertTrue(feats.get(0).startsWith("pos="));
    }
  }
}
