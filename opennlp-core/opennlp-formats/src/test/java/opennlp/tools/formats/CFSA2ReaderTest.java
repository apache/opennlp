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

package opennlp.tools.formats;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Validates the clean-room {@link CFSA2Reader} against a ground-truth automaton. The fixture below
 * was generated once by morfologik's {@code CFSA2Serializer} from the words
 * {@code {cat, cats, do, dog, dogs}} and committed as the expected encoding; the reader must
 * recover exactly those sequences.
 */
public class CFSA2ReaderTest {

  private static final String FIXTURE_BASE64 = "XGZzYcYABwgAdHNvZ2RjYUBeAwYKxePkYgDHYQg=";

  private static List<String> sequences(byte[] cfsa2) throws IOException {
    final CFSA2Reader reader = CFSA2Reader.read(new ByteArrayInputStream(cfsa2));
    final List<String> out = new ArrayList<>();
    reader.forEachSequence(bytes -> out.add(new String(bytes, StandardCharsets.UTF_8)));
    return out;
  }

  /** Every accepted sequence is recovered, in the automaton's stored lexicographic order. */
  @Test
  void testEnumeratesAllAcceptedSequences() throws IOException {
    Assertions.assertEquals(List.of("cat", "cats", "do", "dog", "dogs"),
        sequences(Base64.getDecoder().decode(FIXTURE_BASE64)));
  }

  /** A stream that is not an FSA automaton fails loudly. */
  @Test
  void testRejectsNonFsaMagic() {
    Assertions.assertThrows(IOException.class, () -> CFSA2Reader.read(
        new ByteArrayInputStream("not an fsa header".getBytes(StandardCharsets.UTF_8))));
  }

  /** An FSA of a different version than CFSA2 fails loudly rather than misreading. */
  @Test
  void testRejectsUnsupportedVersion() {
    final byte[] altered = Base64.getDecoder().decode(FIXTURE_BASE64);
    altered[4] = 0x05;
    Assertions.assertThrows(IOException.class,
        () -> CFSA2Reader.read(new ByteArrayInputStream(altered)));
  }

  /** A null stream is rejected at the boundary. */
  @Test
  void testNullStreamRejected() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> CFSA2Reader.read(null));
  }
}
