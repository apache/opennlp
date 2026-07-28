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

package opennlp.spellcheck.dictionary;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import opennlp.tools.ml.model.AbstractModelReader;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the unigram and bigram count fields of a SymSpell model stream are
 * validated against {@link AbstractModelReader#MAX_ENTRIES} before the backing maps are
 * pre-sized, so that a corrupt or out-of-range count fails loud instead of triggering an
 * outsized allocation.
 */
class SymSpellModelSerializerLimitsTest {

  /**
   * Writes a well-formed header up to (and including) the unigram count, optionally
   * followed by a bigram count. A {@code null} bigram count stops after the unigram count.
   */
  private static byte[] stream(int unigramCount, Integer bigramCount) throws IOException {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final DataOutputStream dout = new DataOutputStream(baos);

    dout.writeInt(SymSpellModelSerializer.MAGIC);
    dout.writeInt(SymSpellModelSerializer.FORMAT_VERSION);

    dout.writeUTF("en");
    dout.writeUTF("test");
    dout.writeUTF("1.0");

    dout.writeInt(2);    // maxDictionaryEditDistance
    dout.writeInt(7);    // prefixLength
    dout.writeLong(1L);  // countThreshold
    dout.writeUTF(SymSpellModelSerializer.EDIT_DISTANCE_DAMERAU_OSA);
    dout.writeLong(0L);  // corpusWordCount

    dout.writeInt(unigramCount);
    if (bigramCount != null) {
      dout.writeInt(bigramCount);
    }
    dout.flush();
    return baos.toByteArray();
  }

  private static IOException expectRejection(byte[] bytes) {
    return assertThrows(IOException.class,
        () -> new SymSpellModelSerializer().create(new ByteArrayInputStream(bytes)));
  }

  @Test
  void unigramCountMaxValueIsRejected() throws IOException {
    final IOException e = expectRejection(stream(Integer.MAX_VALUE, null));
    assertTrue(e.getMessage().contains("unigram count"), e.getMessage());
  }

  @Test
  void unigramCountAboveLimitIsRejected() throws IOException {
    final IOException e = expectRejection(stream(AbstractModelReader.MAX_ENTRIES + 1, null));
    assertTrue(e.getMessage().contains("unigram count"), e.getMessage());
  }

  @Test
  void negativeUnigramCountIsRejected() throws IOException {
    final IOException e = expectRejection(stream(-1, null));
    assertTrue(e.getMessage().contains("unigram count"), e.getMessage());
  }

  @Test
  void bigramCountMaxValueIsRejected() throws IOException {
    final IOException e = expectRejection(stream(0, Integer.MAX_VALUE));
    assertTrue(e.getMessage().contains("bigram count"), e.getMessage());
  }

  @Test
  void bigramCountAboveLimitIsRejected() throws IOException {
    final IOException e = expectRejection(stream(0, AbstractModelReader.MAX_ENTRIES + 1));
    assertTrue(e.getMessage().contains("bigram count"), e.getMessage());
  }

  @Test
  void negativeBigramCountIsRejected() throws IOException {
    final IOException e = expectRejection(stream(0, -1));
    assertTrue(e.getMessage().contains("bigram count"), e.getMessage());
  }
}
