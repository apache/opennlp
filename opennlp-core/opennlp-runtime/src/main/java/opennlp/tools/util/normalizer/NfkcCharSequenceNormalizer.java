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
 * A {@link CharSequenceNormalizer} that applies Unicode Normalization Form KC (compatibility
 * composition, UAX #15).
 *
 * <p>NFKC folds compatibility variants to their canonical form: fullwidth and halfwidth letters,
 * the {@code U+FB01} ligature to {@code fi}, and super/subscript digits to plain digits. It is
 * more aggressive than {@link NfcCharSequenceNormalizer NFC} and is lossy (it can change a
 * character's appearance or meaning, e.g. a squared numeral to a plain one), so it is a deliberate
 * choice for search/recall rather than a safe default.</p>
 */
public class NfkcCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 8273650194427108359L;

  private static final NfkcCharSequenceNormalizer INSTANCE = new NfkcCharSequenceNormalizer();

  /** {@return the shared, stateless instance} */
  public static NfkcCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  /** {@inheritDoc} */
  @Override
  public CharSequence normalize(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    return Normalizer.normalize(text, Normalizer.Form.NFKC);
  }
}
