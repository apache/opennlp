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
package opennlp.tools.tokenize;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pins the whitespace definition of {@link BertNormalization} at the code points where it
 * differs from the Unicode {@code White_Space} property. The definition (space, tab, LF, CR,
 * plus the {@code Zs} space separators) is fixed by the reference BERT implementation:
 * pre-trained BERT models were built with exactly this tokenization, so it must not migrate
 * to the Unicode {@code White_Space} set, or subword tokenization would desynchronize from
 * the vocabularies of existing models.
 */
public class BertNormalizationWhitespaceTest {

  @Test
  void testBertWhitespaceIsFrozenAtTheReferenceDefinition() {
    // The four ASCII characters named by the reference implementation.
    Assertions.assertTrue(BertNormalization.isWhitespace(' '));
    Assertions.assertTrue(BertNormalization.isWhitespace('\t'));
    Assertions.assertTrue(BertNormalization.isWhitespace('\n'));
    Assertions.assertTrue(BertNormalization.isWhitespace('\r'));

    // The Zs space separators, including the no-break spaces.
    Assertions.assertTrue(BertNormalization.isWhitespace(0x00A0));
    Assertions.assertTrue(BertNormalization.isWhitespace(0x2007));
    Assertions.assertTrue(BertNormalization.isWhitespace(0x202F));
    Assertions.assertTrue(BertNormalization.isWhitespace(0x3000));

    // Unicode White_Space members that BERT deliberately does not treat as whitespace
    // (they fall into the control bucket instead and are stripped by the cleaning step).
    Assertions.assertFalse(BertNormalization.isWhitespace(0x000B)); // VT
    Assertions.assertFalse(BertNormalization.isWhitespace(0x000C)); // FF
    Assertions.assertFalse(BertNormalization.isWhitespace(0x0085)); // NEL
    Assertions.assertFalse(BertNormalization.isWhitespace(0x2028)); // LINE SEPARATOR
    Assertions.assertFalse(BertNormalization.isWhitespace(0x2029)); // PARAGRAPH SEPARATOR

    // Non-whitespace under both definitions.
    Assertions.assertFalse(BertNormalization.isWhitespace(0x001C));
    Assertions.assertFalse(BertNormalization.isWhitespace(0x200B));
  }
}
