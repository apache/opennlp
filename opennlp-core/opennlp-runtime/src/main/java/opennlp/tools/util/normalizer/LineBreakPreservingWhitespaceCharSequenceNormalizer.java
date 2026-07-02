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
 * An {@link OffsetAwareNormalizer} that collapses runs of Unicode whitespace like
 * {@link WhitespaceCharSequenceNormalizer}, but keeps line and paragraph structure: any whitespace
 * run that contains a line break collapses to a single newline ({@code U+000A}) instead of a space,
 * and leading and trailing whitespace is trimmed.
 *
 * <p>This is the form wanted for readable snippets and display: horizontal runs of spaces and tabs
 * become a single space, yet a blank line between paragraphs survives as one newline rather than
 * being flattened into the surrounding text. It reuses the cursor based
 * {@link CharClass#collapsePreserving(CharSequence, CodePointSet, int)} engine, so it recognizes the
 * full Unicode {@code White_Space} set with no regular expression.</p>
 */
public class LineBreakPreservingWhitespaceCharSequenceNormalizer implements OffsetAwareNormalizer {

  private static final long serialVersionUID = -1773649272610859873L;

  private static final int NEWLINE = 0x000A;

  private static final CharClass WHITESPACE = CharClass.whitespace();

  // The Unicode mandatory break code points (UAX #14 classes BK/CR/LF/NL): line feed, vertical tab,
  // form feed, carriage return, next line, line separator, and paragraph separator. A whitespace run
  // that contains any of these collapses to a single newline rather than a space, so line and
  // paragraph structure survives while horizontal runs are squished.
  private static final CodePointSet LINE_BREAKS = CodePointSet.of(
      0x000A,   // line feed
      0x000B,   // vertical tab
      0x000C,   // form feed
      0x000D,   // carriage return
      0x0085,   // next line
      0x2028,   // line separator
      0x2029);  // paragraph separator

  private static final LineBreakPreservingWhitespaceCharSequenceNormalizer INSTANCE =
      new LineBreakPreservingWhitespaceCharSequenceNormalizer();

  /** {@return the shared, stateless instance} */
  public static LineBreakPreservingWhitespaceCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  @Override
  public CharSequence normalize(CharSequence text) {
    return WHITESPACE.trim(WHITESPACE.collapsePreserving(text, LINE_BREAKS, NEWLINE));
  }

  @Override
  public AlignedText normalizeAligned(CharSequence text) {
    final AlignedText collapsed = WHITESPACE.collapsePreservingAligned(text, LINE_BREAKS, NEWLINE);
    final AlignedText trimmed = WHITESPACE.trimAligned(collapsed.normalized());
    return new AlignedText(text, trimmed.normalized(),
        collapsed.alignment().andThen(trimmed.alignment()));
  }
}
