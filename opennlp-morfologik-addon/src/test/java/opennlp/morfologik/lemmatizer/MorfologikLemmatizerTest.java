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

package opennlp.morfologik.lemmatizer;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import opennlp.morfologik.builder.POSDictionayBuilderTest;
import opennlp.tools.lemmatizer.Lemmatizer;

public class MorfologikLemmatizerTest {

  @Test
  public void testLemmatizeInsensitive() throws Exception {
    Lemmatizer dict = createDictionary(false);


    String[] toks = {"casa", "casa", "Casa"};
    String[] tags = {"V", "NOUN", "PROP"};

    String[] lemmas = dict.lemmatize(toks, tags);

    Assert.assertEquals("casar", lemmas[0]);
    Assert.assertEquals("casa", lemmas[1]);

    // lookup is case insensitive. There is no entry casa - prop
    Assert.assertNull(lemmas[2]);
  }

  @Test
  public void testLemmatizeMultiLemma() throws Exception {
    MorfologikLemmatizer dict = createDictionary(false);

    String[] toks = {"foi"};
    String[] tags = {"V"};

    List<List<String>> lemmas = dict.lemmatize(Arrays.asList(toks), Arrays.asList(tags));

    Assert.assertTrue(lemmas.get(0).contains("ir"));
    Assert.assertTrue(lemmas.get(0).contains("ser"));
  }

  private MorfologikLemmatizer createDictionary(boolean caseSensitive)
      throws Exception {
    Path output = POSDictionayBuilderTest.createMorfologikDictionary();
    return new MorfologikLemmatizer(output);
  }

}
