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

package opennlp.tools.stemmer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class PorterStemmerTest {

  private PorterStemmer stemmer;

  @BeforeEach
  public void setup() {
    stemmer = new PorterStemmer();
  }

  @Test
  void testStem() {
    Assertions.assertEquals("deni", stemmer.stem("deny"));
    Assertions.assertEquals("declin", stemmer.stem("declining"));
    Assertions.assertEquals("divers", stemmer.stem("diversity"));
    Assertions.assertEquals("diver", stemmer.stem("divers"));
    Assertions.assertEquals("dental", stemmer.stem("dental"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"likes", "liked", "likely", "liking"})
  void testStemLike(String input) {
    Assertions.assertEquals("like", stemmer.stem(input));
  }


  @Test // Context: OpenNLP-1229 - This is here to demonstrate & verify.
  void testStemThis() {
    Assertions.assertEquals("thi", stemmer.stem("this"));
  }
}
