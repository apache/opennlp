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
 * A {@link CharSequenceNormalizer} that removes invisible format and bidirectional control
 * characters that add no textual content and are a common source of noise and spoofing (the
 * byte-order mark, zero width space, word joiner, bidi marks/embeddings/overrides/isolates, the
 * invisible math operators, soft hyphen, and the Arabic letter mark).
 *
 * <p>Membership is an O(1) {@link CharClass} lookup and removal is a single cursor pass with no
 * regular expression. The zero width joiner ({@code U+200D}) and non-joiner ({@code U+200C}) are
 * deliberately <em>kept</em>, because they carry meaning in Persian, Indic scripts, and emoji
 * sequences; so are variation selectors. Use this only for a matching/search form, not for
 * display.</p>
 */
public class InvisibleCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 4837512098664301927L;

  // The replacement is unused: removeAll deletes members rather than substituting them.
  private static final CharClass INVISIBLE = CharClass.of(CodePointSet.of(
      0x00AD,   // soft hyphen
      0x061C,   // arabic letter mark
      0x200B,   // zero width space
      0x200E,   // left-to-right mark
      0x200F,   // right-to-left mark
      0x202A,   // left-to-right embedding
      0x202B,   // right-to-left embedding
      0x202C,   // pop directional formatting
      0x202D,   // left-to-right override
      0x202E,   // right-to-left override
      0x2060,   // word joiner
      0x2061,   // function application
      0x2062,   // invisible times
      0x2063,   // invisible separator
      0x2064,   // invisible plus
      0x2066,   // left-to-right isolate
      0x2067,   // right-to-left isolate
      0x2068,   // first strong isolate
      0x2069,   // pop directional isolate
      0xFEFF),  // zero width no-break space (byte order mark)
      0x0020);

  private static final InvisibleCharSequenceNormalizer INSTANCE =
      new InvisibleCharSequenceNormalizer();

  /** {@return the shared, stateless instance} */
  public static InvisibleCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  @Override
  public CharSequence normalize(CharSequence text) {
    return INVISIBLE.removeAll(text);
  }

}
