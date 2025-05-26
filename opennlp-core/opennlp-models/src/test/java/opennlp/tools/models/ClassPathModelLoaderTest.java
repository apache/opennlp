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
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.models.simple.SimpleClassPathModelFinder;
import opennlp.tools.postag.POSModel;
import opennlp.tools.tokenize.TokenizerModel;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClassPathModelLoaderTest {

  // SUT
  private ClassPathModelLoader loader;

  @BeforeEach
  void setUp() {
    loader = new ClassPathModelLoader();
  }

  @Test
  void testLoadModelWithEmptyClassPathEntriesShouldReturnNull() throws IOException {
    assertNull(loader.load(Collections.emptySet(), "en", "pos", POSModel.class));
  }

  @Test
  void testLoadModelWithUnsupportedLanguageCodeShouldReturnNull() throws IOException {
    assertNull(loader.load(Collections.emptySet(), "xy", "pos", POSModel.class));
  }

  @Test
  void testLoadModelWithIllegalModelTypeShouldReturnNull() throws IOException {
    assertNull(loader.load(Collections.emptySet(), "en", "xyz", POSModel.class));
  }

  @Test
  void testLoadModelWithInconsistentClassTypeShouldTrow() {
    SimpleClassPathModelFinder finder = new SimpleClassPathModelFinder();
    // Check if a mismatch is correctly detected and thrown
    assertThrows(ClassPathLoaderException.class, () ->
            loader.load(finder.findModels(true), "en", "pos", TokenizerModel.class));
  }

  @Test
  void testLoadModelWithIllegalArguments1() {
    assertThrows(IllegalArgumentException.class, ()
            -> loader.load(null, "en", "pos", POSModel.class));
  }

  @Test
  void testLoadModelWithIllegalArguments2() {
    assertThrows(IllegalArgumentException.class, ()
            -> loader.load(null, "en", ModelType.POS_GENERIC, POSModel.class));
  }

  @Test
  void testLoadModelWithIllegalArguments3a() {
    assertThrows(IllegalArgumentException.class, ()
            -> loader.load(Collections.emptySet(), null, ModelType.POS_GENERIC, POSModel.class));
  }

  @Test
  void testLoadModelWithIllegalArguments3b() {
    assertThrows(IllegalArgumentException.class, ()
            -> loader.load(Collections.emptySet(), " ", ModelType.POS_GENERIC, POSModel.class));
  }

  @Test
  void testLoadModelWithIllegalArguments4a() {
    assertThrows(IllegalArgumentException.class, ()
            -> loader.load(Collections.emptySet(), "en", (ModelType) null, POSModel.class));
  }

  @Test
  void testLoadModelWithIllegalArguments4b() {
    assertThrows(IllegalArgumentException.class, ()
            -> loader.load(Collections.emptySet(), "en", (String) null, POSModel.class));
  }
}
