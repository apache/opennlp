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
package opennlp.tools.embeddings;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextEmbedderTest {

  private final TextEmbedder permissiveEmbedder = new TextEmbedder() {
    @Override
    public float[] embed(CharSequence text) {
      return new float[] {1f};
    }

    @Override
    public int dimension() {
      return 1;
    }
  };

  @Test
  void testEmbedAllRejectsNullList() {
    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> permissiveEmbedder.embedAll(null));

    assertEquals("texts must not be null", exception.getMessage());
  }

  @Test
  void testEmbedAllRejectsNullElement() {
    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> permissiveEmbedder.embedAll(Arrays.asList("first", null)));

    assertEquals("texts[1] must not be null", exception.getMessage());
  }
}
