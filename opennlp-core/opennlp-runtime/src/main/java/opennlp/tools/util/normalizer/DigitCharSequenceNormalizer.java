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

/**
 * A {@link CharSequenceNormalizer} that maps Unicode decimal digits to their ASCII equivalents,
 * so for example Arabic-Indic, Devanagari, or fullwidth digits all become {@code 0}-{@code 9}.
 *
 * <p>It maps a code point when {@link Character#digit(int, int)} reports a value of {@code 0}-
 * {@code 9} in radix ten, that is, when the code point is a Unicode decimal digit. Other numeric
 * forms (Roman numerals, superscripts, circled numbers, fractions) are not decimal digits and are
 * left unchanged. Scanning is a single O(1)-per-code-point cursor pass with no regular
 * expression.</p>
 */
public class DigitCharSequenceNormalizer implements OffsetAwareNormalizer {

  private static final long serialVersionUID = -3478452280126315708L;

  private static final DigitCharSequenceNormalizer INSTANCE = new DigitCharSequenceNormalizer();

  /** {@return the shared, stateless instance} */
  public static DigitCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  @Override
  public CharSequence normalize(CharSequence text) {
    return CharClass.substitute(text, DigitCharSequenceNormalizer::toAscii);
  }

  // The ASCII digit for a Unicode decimal digit code point, or null to copy the code point through.
  private static String toAscii(int codePoint) {
    final int value = Character.digit(codePoint, 10);
    return value >= 0 ? String.valueOf((char) ('0' + value)) : null;
  }

  @Override
  public AlignedText normalizeAligned(CharSequence text) {
    return CharClass.substituteAligned(text, DigitCharSequenceNormalizer::toAscii);
  }
}
