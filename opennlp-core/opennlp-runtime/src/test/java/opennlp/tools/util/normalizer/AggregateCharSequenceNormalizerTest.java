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

package opennlp.tools.util.normalizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AggregateCharSequenceNormalizerTest {

  @Test
  void testAppliesNormalizersInConstructionOrder() {
    CharSequenceNormalizer upper = text -> text.toString().replace('a', 'B');
    CharSequenceNormalizer strip = text -> text.toString().replace("B", "");
    assertEquals("cc", new AggregateCharSequenceNormalizer(upper, strip)
        .normalize("accB").toString());
    assertEquals("Bcc", new AggregateCharSequenceNormalizer(strip, upper)
        .normalize("accB").toString());
  }

  @Test
  void testRejectsNullNormalizersLoudly() {
    IllegalArgumentException nullArray = assertThrows(IllegalArgumentException.class,
        () -> new AggregateCharSequenceNormalizer((CharSequenceNormalizer[]) null));
    assertEquals("The normalizers must not be null.", nullArray.getMessage());

    IllegalArgumentException nullElement = assertThrows(IllegalArgumentException.class,
        () -> new AggregateCharSequenceNormalizer(
            NfcCharSequenceNormalizer.getInstance(), null));
    assertEquals("The normalizers must not contain null.", nullElement.getMessage());
  }

  @Test
  void testChangingTheCallerArrayDoesNotReachTheAggregate() {
    CharSequenceNormalizer[] normalizers = {NfcCharSequenceNormalizer.getInstance()};
    AggregateCharSequenceNormalizer aggregate =
        new AggregateCharSequenceNormalizer(normalizers);
    normalizers[0] = text -> "changed";
    assertEquals("abc", aggregate.normalize("abc").toString());
  }

  @Test
  void testRejectsNullTextLoudly() {
    AggregateCharSequenceNormalizer aggregate = new AggregateCharSequenceNormalizer();
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> aggregate.normalize(null));
    assertEquals("The text must not be null.", e.getMessage());
  }
}
