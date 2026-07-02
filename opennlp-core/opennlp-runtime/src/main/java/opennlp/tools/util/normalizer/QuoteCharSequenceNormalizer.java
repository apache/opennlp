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
 * A {@link CharSequenceNormalizer} that folds typographic quotation marks to their ASCII forms:
 * the single quotes and apostrophes to {@code '} and the double quotes to {@code "}.
 *
 * <p>This is high value for matching, since curly quotes, guillemets, and fullwidth quotes
 * otherwise prevent {@code "don't"} from matching {@code "don" + U+2019 + "t"}. It is built from
 * two {@link CharClass} sets, so membership is O(1) and scanning is a single cursor pass with no
 * regular expression. ASCII quotes are left unchanged.</p>
 */
public class QuoteCharSequenceNormalizer implements OffsetAwareNormalizer {

  private static final long serialVersionUID = 6135398427498177491L;

  // Single quotes / apostrophes -> U+0027 APOSTROPHE.
  private static final CharClass SINGLE = CharClass.of(CodePointSet.of(
      0x2018,   // left single quotation mark
      0x2019,   // right single quotation mark
      0x201A,   // single low-9 quotation mark
      0x201B,   // single high-reversed-9 quotation mark
      0x2039,   // single left-pointing angle quotation mark
      0x203A,   // single right-pointing angle quotation mark
      0x02BC,   // modifier letter apostrophe
      0xFF07),  // fullwidth apostrophe
      '\'');

  // Double quotes -> U+0022 QUOTATION MARK.
  private static final CharClass DOUBLE = CharClass.of(CodePointSet.of(
      0x201C,   // left double quotation mark
      0x201D,   // right double quotation mark
      0x201E,   // double low-9 quotation mark
      0x201F,   // double high-reversed-9 quotation mark
      0x00AB,   // left-pointing double angle quotation mark
      0x00BB,   // right-pointing double angle quotation mark
      0x301D,   // reversed double prime quotation mark
      0x301E,   // double prime quotation mark
      0x301F,   // low double prime quotation mark
      0xFF02),  // fullwidth quotation mark
      '"');

  private static final QuoteCharSequenceNormalizer INSTANCE = new QuoteCharSequenceNormalizer();

  /** {@return the shared, stateless instance} */
  public static QuoteCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  @Override
  public CharSequence normalize(CharSequence text) {
    return DOUBLE.normalize(SINGLE.normalize(text));
  }

  @Override
  public AlignedText normalizeAligned(CharSequence text) {
    final AlignedText single = SINGLE.normalizeAligned(text);
    final AlignedText both = DOUBLE.normalizeAligned(single.normalized());
    return new AlignedText(text, both.normalized(),
        single.alignment().andThen(both.alignment()));
  }
}
