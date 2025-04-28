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

package opennlp.tools.models;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.models.simple.SimpleClassPathModelFinder;
import opennlp.tools.postag.POSModel;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerModel;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DefaultClassPathModelProviderTest {

  private static SimpleClassPathModelFinder finder;
  private static ClassPathModelLoader loader;

  // SUT
  private DefaultClassPathModelProvider provider;

  @BeforeAll
  public static void initEnv() {
    finder = new SimpleClassPathModelFinder();
    loader = new ClassPathModelLoader();
  }

  @BeforeEach
  void setup() {
    provider = new DefaultClassPathModelProvider(finder, loader);
  }

  @Test
  void testLoadSentenceModel() throws IOException {
    SentenceModel m = provider.load("en", ModelType.SENTENCE_DETECTOR, SentenceModel.class);
    assertNotNull(m);
    assertTrue(m.isLoadedFromSerialized());
  }

  @Test
  void testLoadTokenizerModel() throws IOException {
    TokenizerModel m = provider.load("en", ModelType.TOKENIZER, TokenizerModel.class);
    assertNotNull(m);
    assertTrue(m.isLoadedFromSerialized());
  }

  @Test
  void testLoadPOSModel() throws IOException {
    POSModel m = provider.load("en", ModelType.POS_GENERIC, POSModel.class);
    assertNotNull(m);
    assertTrue(m.isLoadedFromSerialized());
  }

  @Test
  void testCreateInstance() {
    try {
      new DefaultClassPathModelProvider();
    } catch (RuntimeException e) {
      fail(e.getLocalizedMessage(), e);
    }
  }

  @Test
  void testCreateInstanceWithIllegalParameters1() {
    assertThrows(IllegalArgumentException.class, () ->
            new DefaultClassPathModelProvider(finder, null));
  }

  @Test
  void testCreateInstanceWithIllegalParameters2() {
    assertThrows(IllegalArgumentException.class, () ->
            new DefaultClassPathModelProvider(null, loader));
  }
}
