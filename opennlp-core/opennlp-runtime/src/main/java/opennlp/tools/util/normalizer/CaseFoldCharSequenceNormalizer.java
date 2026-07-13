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

import java.util.Locale;
import java.util.Objects;

/**
 * A {@link CharSequenceNormalizer} that lower cases text for case-insensitive matching. It uses
 * {@link Locale#ROOT} by default, so the result does not depend on the JVM's default locale.
 *
 * <p>{@code Locale.ROOT} avoids locale surprises such as the Turkish dotless-i
 * mapping. A specific locale can be supplied through {@link #CaseFoldCharSequenceNormalizer(Locale)}
 * or {@link #getInstance(Locale)} when a language's case rules are wanted (Turkish being the classic
 * example). Full Unicode case folding (for example German eszett, {@code U+00DF}, to {@code ss}) is
 * a distinct, heavier transform and is intentionally out of scope here.</p>
 */
public class CaseFoldCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 9183472265510038829L;

  private static final CaseFoldCharSequenceNormalizer INSTANCE =
      new CaseFoldCharSequenceNormalizer();

  /** The locale whose case rules are applied. */
  private final Locale locale;

  /** Creates a normalizer that lower cases using {@link Locale#ROOT}. */
  public CaseFoldCharSequenceNormalizer() {
    this(Locale.ROOT);
  }

  /**
   * Creates a normalizer that lower cases using the given locale.
   *
   * @param locale The locale whose case rules to apply.
   */
  public CaseFoldCharSequenceNormalizer(Locale locale) {
    this.locale = Objects.requireNonNull(locale,
        "locale must not be null; call getInstance() for the locale-independent default");
  }

  /** {@return the shared, stateless {@link Locale#ROOT} instance} */
  public static CaseFoldCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  /**
   * {@return a normalizer for the given locale} The shared {@link Locale#ROOT} instance is returned
   * for {@code Locale.ROOT}.
   *
   * @param locale The locale whose case rules to apply.
   * @throws NullPointerException Thrown if {@code locale} is null; call {@link #getInstance()} for
   *     the locale-independent default.
   */
  public static CaseFoldCharSequenceNormalizer getInstance(Locale locale) {
    Objects.requireNonNull(locale,
        "locale must not be null; call getInstance() for the locale-independent default");
    return Locale.ROOT.equals(locale) ? INSTANCE : new CaseFoldCharSequenceNormalizer(locale);
  }

  /** {@inheritDoc} */
  @Override
  public CharSequence normalize(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    return text.toString().toLowerCase(locale);
  }
}
