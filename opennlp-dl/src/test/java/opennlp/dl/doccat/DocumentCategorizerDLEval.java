/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl.doccat;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

public class DocumentCategorizerDLEval {

  @Test
  public void categorize() throws URISyntaxException {

    // This test was written using the nlptown/bert-base-multilingual-uncased-sentiment model.
    // You will need to update the assertions if you use a different model.

    final File model = new File(getClass().getClassLoader().getResource("doccat/model.onnx").toURI());
    final File vocab = new File(getClass().getClassLoader().getResource("doccat/vocab.txt").toURI());

    final DocumentCategorizerDL documentCategorizerDL =
            new DocumentCategorizerDL(model, vocab, getCategories());

    final double[] result = documentCategorizerDL.categorize(new String[]{"I am happy"});
    System.out.println(Arrays.toString(result));

    final double[] expected = new double[]
        {0.007819971069693565,
        0.006593209225684404,
        0.04995147883892059,
        0.3003573715686798,
        0.6352779865264893};

    Assert.assertTrue(Arrays.equals(expected, result));
    Assert.assertEquals(5, result.length);

    final String category = documentCategorizerDL.getBestCategory(result);
    Assert.assertEquals("very good", category);

  }

  @Test
  public void scoreMap() throws URISyntaxException {

    // This test was written using the nlptown/bert-base-multilingual-uncased-sentiment model.
    // You will need to update the assertions if you use a different model.

    final File model = new File(getClass().getClassLoader().getResource("doccat/model.onnx").toURI());
    final File vocab = new File(getClass().getClassLoader().getResource("doccat/vocab.txt").toURI());

    final DocumentCategorizerDL documentCategorizerDL =
            new DocumentCategorizerDL(model, vocab, getCategories());

    final Map<String, Double> result = documentCategorizerDL.scoreMap(new String[]{"I am happy"});

    Assert.assertEquals(0.6352779865264893, result.get("very good").doubleValue(), 0);
    Assert.assertEquals(0.3003573715686798, result.get("good").doubleValue(), 0);
    Assert.assertEquals(0.04995147883892059, result.get("neutral").doubleValue(), 0);
    Assert.assertEquals(0.006593209225684404, result.get("bad").doubleValue(), 0);
    Assert.assertEquals(0.007819971069693565, result.get("very bad").doubleValue(), 0);

  }

  @Test
  public void sortedScoreMap() throws URISyntaxException {

    // This test was written using the nlptown/bert-base-multilingual-uncased-sentiment model.
    // You will need to update the assertions if you use a different model.

    final File model = new File(getClass().getClassLoader().getResource("doccat/model.onnx").toURI());
    final File vocab = new File(getClass().getClassLoader().getResource("doccat/vocab.txt").toURI());

    final DocumentCategorizerDL documentCategorizerDL =
            new DocumentCategorizerDL(model, vocab, getCategories());

    final Map<Double, Set<String>> result = documentCategorizerDL.sortedScoreMap(new String[]{"I am happy"});

    Assert.assertEquals(result.get(0.6352779865264893).size(), 1);
    Assert.assertEquals(result.get(0.3003573715686798).size(), 1);
    Assert.assertEquals(result.get(0.04995147883892059).size(), 1);
    Assert.assertEquals(result.get(0.006593209225684404).size(), 1);
    Assert.assertEquals(result.get(0.007819971069693565).size(), 1);

  }

  @Test
  public void doccat() throws URISyntaxException {

    // This test was written using the nlptown/bert-base-multilingual-uncased-sentiment model.
    // You will need to update the assertions if you use a different model.

    final File model = new File(getClass().getClassLoader().getResource("doccat/model.onnx").toURI());
    final File vocab = new File(getClass().getClassLoader().getResource("doccat/vocab.txt").toURI());

    final DocumentCategorizerDL documentCategorizerDL =
            new DocumentCategorizerDL(model, vocab, getCategories());

    final int index = documentCategorizerDL.getIndex("bad");
    Assert.assertEquals(1, index);

    final String category = documentCategorizerDL.getCategory(3);
    Assert.assertEquals("good", category);

    final int number = documentCategorizerDL.getNumberOfCategories();
    Assert.assertEquals(5, number);

  }

  private Map<Integer, String> getCategories() {

    final Map<Integer, String> categories = new HashMap<>();

    categories.put(0, "very bad");
    categories.put(1, "bad");
    categories.put(2, "neutral");
    categories.put(3, "good");
    categories.put(4, "very good");

    return categories;

  }

}
