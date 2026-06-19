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

/**
 * A {@link CharSequenceNormalizer} that lower cases text for case-insensitive matching, using
 * {@link Locale#ROOT} so the result does not depend on the JVM's default locale.
 *
 * <p>This is the case-folding step of a search / BM25 analysis chain (the counterpart to Lucene's
 * lower-case filter). {@code Locale.ROOT} avoids locale surprises such as the Turkish dotless-i
 * mapping; callers that need language-specific case rules should fold with an explicit locale
 * upstream. Full Unicode case folding (for example German eszett, {@code U+00DF}, to {@code ss})
 * is a distinct, heavier transform and is intentionally out of scope here.</p>
 */
public class CaseFoldCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 1L;

  private static final CaseFoldCharSequenceNormalizer INSTANCE =
      new CaseFoldCharSequenceNormalizer();

  /** {@return the shared, stateless instance} */
  public static CaseFoldCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  @Override
  public CharSequence normalize(CharSequence text) {
    return text.toString().toLowerCase(Locale.ROOT);
  }
}
