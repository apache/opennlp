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

import java.text.Normalizer;

/**
 * A {@link CharSequenceNormalizer} that applies Unicode Normalization Form C (canonical
 * composition, UAX #15).
 *
 * <p>NFC is the safe, lossless (under canonical equivalence) baseline for matching: precomposed
 * and decomposed spellings of the same text (for example {@code U+00E9} versus {@code e} plus a
 * combining acute accent) become identical, so equal text compares equal regardless of how it was
 * encoded. It changes no characters' meaning and is the W3C-recommended interchange form.</p>
 */
public class NfcCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 5960421783304915649L;

  private static final NfcCharSequenceNormalizer INSTANCE = new NfcCharSequenceNormalizer();

  /** {@return the shared, stateless instance} */
  public static NfcCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  /** {@inheritDoc} */
  @Override
  public CharSequence normalize(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    return Normalizer.normalize(text, Normalizer.Form.NFC);
  }
}
