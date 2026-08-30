/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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
 * An {@link OffsetAwareNormalizer} that unwraps hard-wrapped prose while keeping real paragraph
 * breaks. Within each whitespace run, at most one logical line break (including {@code CRLF} as a
 * single break) collapses to a single space; two or more collapse to one newline
 * ({@code U+000A}). Leading and trailing whitespace is trimmed.
 *
 * <p>This is the form wanted before sentence detection on Gutenberg-style or other fixed-width
 * plain text: intra-paragraph line wraps become spaces, blank-line paragraph boundaries survive as
 * newlines. It reuses the cursor based {@link CharClass#collapseParagraphPreserving(CharSequence,
 * CodePointSet, int)} engine, so it recognizes the full Unicode {@code White_Space} set with no
 * regular expression.</p>
 *
 * <p>For display-oriented text where every line break should survive, use
 * {@link LineBreakPreservingWhitespaceCharSequenceNormalizer} instead. For raw markdown with lists,
 * code blocks, or other single-newline structure, apply this normalizer only to extracted prose.</p>
 */
public class ParagraphPreservingWhitespaceCharSequenceNormalizer implements OffsetAwareNormalizer {

  private static final long serialVersionUID = 8923746519823746519L;

  private static final int NEWLINE = 0x000A;

  private static final CharClass WHITESPACE = CharClass.whitespace();

  // The Unicode mandatory break code points (UAX #14 classes BK/CR/LF/NL): line feed, vertical tab,
  // form feed, carriage return, next line, line separator, and paragraph separator. Two or more
  // logical breaks in a whitespace run collapse to a single newline; a single break collapses to a
  // space so hard wraps unwrap without splitting sentences.
  private static final CodePointSet LINE_BREAKS = CodePointSet.of(
      0x000A,   // line feed
      0x000B,   // vertical tab
      0x000C,   // form feed
      0x000D,   // carriage return
      0x0085,   // next line
      0x2028,   // line separator
      0x2029);  // paragraph separator

  private static final ParagraphPreservingWhitespaceCharSequenceNormalizer INSTANCE =
      new ParagraphPreservingWhitespaceCharSequenceNormalizer();

  /** {@return the shared, stateless instance} */
  public static ParagraphPreservingWhitespaceCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  @Override
  public CharSequence normalize(CharSequence text) {
    return WHITESPACE.trim(WHITESPACE.collapseParagraphPreserving(text, LINE_BREAKS, NEWLINE));
  }

  @Override
  public AlignedText normalizeAligned(CharSequence text) {
    final AlignedText collapsed =
        WHITESPACE.collapseParagraphPreservingAligned(text, LINE_BREAKS, NEWLINE);
    final AlignedText trimmed = WHITESPACE.trimAligned(collapsed.normalized());
    return new AlignedText(text, trimmed.normalized(),
        collapsed.alignment().andThen(trimmed.alignment()));
  }
}
