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
 * A {@link CharSequenceNormalizer} that replaces unambiguous list-bullet characters with a space,
 * so a bullet acts as a token separator rather than sticking to the following word.
 *
 * <p>Membership is an O(1) {@link CharClass} lookup and scanning is a single cursor pass with no
 * regular expression. The middle dot ({@code U+00B7}) is deliberately <em>not</em> included,
 * because it is a letter in Catalan ({@code l..l}) and other orthographies; only characters that
 * are unambiguously list bullets are replaced.</p>
 */
public class BulletCharSequenceNormalizer implements OffsetAwareNormalizer {

  private static final long serialVersionUID = 5521093348871625541L;

  private static final CharClass BULLETS = CharClass.of(CodePointSet.of(
      0x2022,   // bullet
      0x2023,   // triangular bullet
      0x2043,   // hyphen bullet
      0x2219,   // bullet operator
      0x25E6),  // white bullet
      0x0020);

  private static final BulletCharSequenceNormalizer INSTANCE = new BulletCharSequenceNormalizer();

  /** {@return the shared, stateless instance} */
  public static BulletCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  @Override
  public CharSequence normalize(CharSequence text) {
    return BULLETS.normalize(text);
  }

  @Override
  public AlignedText normalizeAligned(CharSequence text) {
    return BULLETS.normalizeAligned(text);
  }
}
