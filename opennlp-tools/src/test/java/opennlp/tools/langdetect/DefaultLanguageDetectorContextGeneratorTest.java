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

import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Collection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class DefaultLanguageDetectorContextGeneratorTest {

  @Test
  void extractContext() {
    String doc = "abcde fghijk";

    DefaultLanguageDetectorContextGenerator cg = new DefaultLanguageDetectorContextGenerator(1, 3);

    Collection<CharSequence> features = Arrays.asList(cg.getContext(doc));

    Assertions.assertEquals(33, features.size());
    Assertions.assertTrue(features.contains(CharBuffer.wrap("ab")));
    Assertions.assertTrue(features.contains(CharBuffer.wrap("abc")));
    Assertions.assertTrue(features.contains(CharBuffer.wrap("e f")));
    Assertions.assertTrue(features.contains(CharBuffer.wrap(" fg")));
  }
}
