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
 * A {@link CharSequenceNormalizer} that maps every Unicode dash to an ASCII hyphen-minus
 * ({@code U+002D}), reusing the cursor based {@link CharClass#dashes()} engine.
 *
 * <p>This folds the many dash code points (en dash, em dash, figure dash, non-breaking hyphen,
 * fullwidth hyphen, and so on) to a single form so that {@code "state-of-the-art"} matches
 * regardless of which dash the source used. The mathematical minus signs are left untouched by
 * default, and {@code U+00AD} SOFT HYPHEN (a format character) is not treated as a dash.</p>
 */
public class DashCharSequenceNormalizer implements OffsetAwareNormalizer {

  private static final long serialVersionUID = 6620885194730155303L;

  private static final CharClass DASHES = CharClass.dashes();

  private static final DashCharSequenceNormalizer INSTANCE = new DashCharSequenceNormalizer();

  /** {@return the shared, stateless instance} */
  public static DashCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  @Override
  public CharSequence normalize(CharSequence text) {
    return DASHES.normalize(text);
  }

  @Override
  public AlignedText normalizeAligned(CharSequence text) {
    return DASHES.normalizeAligned(text);
  }
}
