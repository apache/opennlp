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

import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;


public class DefaultLanguageDetectorContextGeneratorTest {

  @Test
  public void extractContext() throws Exception {
    String doc = "abcde fghijk";

    LanguageDetectorContextGenerator cg = new DefaultLanguageDetectorContextGenerator(1, 3);

    Collection<String> features = Arrays.asList(cg.getContext(doc));

    Assert.assertEquals(33, features.size());
    Assert.assertTrue(features.contains("ab"));
    Assert.assertTrue(features.contains("abc"));
    Assert.assertTrue(features.contains("e f"));
    Assert.assertTrue(features.contains(" fg"));
  }
}
