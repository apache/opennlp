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
 * Validates the clean-room {@link FSA5Reader} against a ground-truth automaton generated once by
 * morfologik's {@code FSA5Serializer} from the words {@code {cat, cats, do, dog, dogs}}.
 */
public class FSA5ReaderTest {

  private static final String FIXTURE_BASE64 = "XGZzYQVfKwEAAF4GY3BkBm8HZwdzA2EGdGM=";

  private static List<String> sequences(FsaSequenceReader reader) {
    final List<String> out = new ArrayList<>();
    reader.forEachSequence(bytes -> out.add(new String(bytes, StandardCharsets.UTF_8)));
    return out;
  }

  /** Every accepted sequence is recovered, in stored lexicographic order. */
  @Test
  void testEnumeratesAllAcceptedSequences() throws IOException {
    final FSA5Reader reader = FSA5Reader.read(
        new ByteArrayInputStream(Base64.getDecoder().decode(FIXTURE_BASE64)));
    Assertions.assertEquals(List.of("cat", "cats", "do", "dog", "dogs"), sequences(reader));
  }

  /** The format-agnostic dispatcher recognizes and reads FSA5 by its version byte. */
  @Test
  void testDispatcherReadsFsa5() throws IOException {
    final FsaSequenceReader reader = FsaSequenceReader.read(
        new ByteArrayInputStream(Base64.getDecoder().decode(FIXTURE_BASE64)));
    Assertions.assertEquals(List.of("cat", "cats", "do", "dog", "dogs"), sequences(reader));
  }

  /** A different FSA version fails loudly rather than misreading. */
  @Test
  void testRejectsUnsupportedVersion() {
    final byte[] altered = Base64.getDecoder().decode(FIXTURE_BASE64);
    altered[4] = (byte) 0x99;
    Assertions.assertThrows(IOException.class,
        () -> FSA5Reader.read(new ByteArrayInputStream(altered)));
  }

  /** A null stream is rejected at the boundary. */
  @Test
  void testNullStreamRejected() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> FSA5Reader.read(null));
  }
}
