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

package opennlp.spellcheck.stream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.spellcheck.SpellChecker;
import opennlp.spellcheck.dictionary.SymSpellModel;
import opennlp.spellcheck.normalizer.SpellCheckingCharSequenceNormalizer;
import opennlp.spellcheck.symspell.SymSpell;
import opennlp.spellcheck.symspell.TinyDictionary;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SpellCorrectingObjectStreamTest {

  private SymSpell symSpell;

  @BeforeEach
  void setUp() throws Exception {
    symSpell = TinyDictionary.load();
  }

  private static InputStreamFactory lines(String text) {
    return () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
  }

  private static ObjectStream<String> lineStream(String text) throws IOException {
    return new PlainTextByLineStream(lines(text), StandardCharsets.UTF_8);
  }

  @Test
  void correctsEachLine() throws IOException {
    try (ObjectStream<String> stream =
             new SpellCorrectingObjectStream(lineStream("quikc broen fox\nwrold dog\n"), symSpell)) {
      assertEquals("quick brown fox", stream.read());
      assertEquals("world dog", stream.read());
      assertNull(stream.read());
    }
  }

  @Test
  void emitsNullOnExhaustion() throws IOException {
    try (ObjectStream<String> stream =
             new SpellCorrectingObjectStream(lineStream("wrold\n"), symSpell)) {
      assertEquals("world", stream.read());
      assertNull(stream.read());
      // Repeated reads after exhaustion stay null.
      assertNull(stream.read());
    }
  }

  @Test
  void resetReplaysCorrectedLines() throws IOException {
    try (ObjectStream<String> stream =
             new SpellCorrectingObjectStream(lineStream("quikc\nwrold\n"), symSpell)) {
      assertEquals("quick", stream.read());
      assertEquals("world", stream.read());
      stream.reset();
      assertEquals("quick", stream.read());
      assertEquals("world", stream.read());
    }
  }

  @Test
  void closeDelegatesToWrappedStream() throws IOException {
    final boolean[] closed = {false};
    final ObjectStream<String> source = new ObjectStream<>() {
      @Override
      public String read() {
        return null;
      }

      @Override
      public void close() {
        closed[0] = true;
      }
    };
    new SpellCorrectingObjectStream(source, symSpell).close();
    org.junit.jupiter.api.Assertions.assertTrue(closed[0]);
  }

  @Test
  void nullArgumentsAreRejected() throws IOException {
    assertThrows(IllegalArgumentException.class,
        () -> new SpellCorrectingObjectStream(null, symSpell));
    try (ObjectStream<String> samples = lineStream("a\n")) {
      assertThrows(IllegalArgumentException.class,
          () -> new SpellCorrectingObjectStream(samples, (SpellChecker) null));
      assertThrows(IllegalArgumentException.class,
          () -> new SpellCorrectingObjectStream(samples, (SymSpellModel) null));
      assertThrows(IllegalArgumentException.class,
          () -> new SpellCorrectingObjectStream(samples,
              (SpellCheckingCharSequenceNormalizer) null));
    }
  }
}
