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

package opennlp.tools.doccat;

import java.io.IOException;
import java.util.Set;
import java.util.SortedMap;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.TrainingParameters;

public class DocumentCategorizerMETest {

  @Test
  public void testSimpleTraining() throws IOException {

    ObjectStream<DocumentSample> samples = ObjectStreamUtils.createObjectStream(
        new DocumentSample("1", new String[]{"a", "b", "c"}),
        new DocumentSample("1", new String[]{"a", "b", "c", "1", "2"}),
        new DocumentSample("1", new String[]{"a", "b", "c", "3", "4"}),
        new DocumentSample("0", new String[]{"x", "y", "z"}),
        new DocumentSample("0", new String[]{"x", "y", "z", "5", "6"}),
        new DocumentSample("0", new String[]{"x", "y", "z", "7", "8"}));

    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ITERATIONS_PARAM, Integer.toString(100));
    params.put(TrainingParameters.CUTOFF_PARAM, Integer.toString(0));

    DoccatModel model = DocumentCategorizerME.train("x-unspecified", samples,
            params, new DoccatFactory());

    DocumentCategorizer doccat = new DocumentCategorizerME(model);

    double aProbs[] = doccat.categorize("a");
    Assert.assertEquals("1", doccat.getBestCategory(aProbs));

    double bProbs[] = doccat.categorize("x");
    Assert.assertEquals("0", doccat.getBestCategory(bProbs));

    //test to make sure sorted map's last key is cat 1 because it has the highest score.
    SortedMap<Double, Set<String>> sortedScoreMap = doccat.sortedScoreMap("a");
    for (String cat : sortedScoreMap.get(sortedScoreMap.lastKey())) {
      Assert.assertEquals("1", cat);
      break;
    }
    System.out.println("");

  }
}
