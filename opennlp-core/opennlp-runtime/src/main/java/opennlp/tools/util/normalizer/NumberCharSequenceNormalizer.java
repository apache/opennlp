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
 * A {@link CharSequenceNormalizer} implementation that normalizes text in terms of numbers:
 * every maximal run of ASCII digits ({@code 0} to {@code 9}), that is the longest unbroken
 * stretch of consecutive digits, is replaced by a single space. For example, {@code "a1234b56"}
 * becomes {@code "a b "}. Non-ASCII digits, for example Arabic-Indic or fullwidth digits, are
 * not treated as digits and are left unchanged.
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
   * {@inheritDoc}
   */
  @Override
  public CharSequence normalize(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    return ASCII_DIGITS.collapse(text);
  }
}
