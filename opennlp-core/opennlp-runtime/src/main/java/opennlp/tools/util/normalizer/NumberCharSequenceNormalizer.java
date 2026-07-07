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
 * A {@link CharSequenceNormalizer} implementation that normalizes text
 * in terms of numbers: every maximal run of ASCII digits ({@code 0} to {@code 9}) is replaced
 * by a single whitespace.
 *
 * <p>This reproduces, byte for byte, the output of the former regex implementation
 * ({@code "\\d+"} replaced by a space; the {@code \d} class matches ASCII digits only), but runs
 * as a single forward cursor scan on the {@link CharClass} engine instead of a regular
 * expression. Non-ASCII digits, for example Arabic-Indic or fullwidth digits, are left
 * unchanged, exactly as before.</p>
 */
public class NumberCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = -782056416383201122L;

  private static final CharClass ASCII_DIGITS =
      CharClass.of(CodePointSet.ofRange('0', '9'), ' ');

  private static final NumberCharSequenceNormalizer INSTANCE = new NumberCharSequenceNormalizer();

  public static NumberCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  /**
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  @Override
  public CharSequence normalize(CharSequence text) {
    return ASCII_DIGITS.collapse(text);
  }
}
