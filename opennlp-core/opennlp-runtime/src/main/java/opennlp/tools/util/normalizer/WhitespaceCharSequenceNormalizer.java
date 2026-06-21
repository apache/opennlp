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
 * A {@link CharSequenceNormalizer} that collapses each run of Unicode whitespace to a single ASCII
 * space and trims the edges, reusing the cursor based {@link CharClass#whitespace()} engine.
 *
 * <p>Unlike a {@code \s} regular expression, this recognizes the full Unicode {@code White_Space}
 * set (no-break space, ideographic space, the typographic spaces, line and paragraph separators,
 * and so on), so spacing copied from the web, PDFs, or non-Latin sources normalizes consistently.
 * It is the Unicode-aware, regex-free counterpart to {@link ShrinkCharSequenceNormalizer}.</p>
 */
public class WhitespaceCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 6748290315562094783L;

  private static final CharClass WHITESPACE = CharClass.whitespace();

  private static final WhitespaceCharSequenceNormalizer INSTANCE =
      new WhitespaceCharSequenceNormalizer();

  /** {@return the shared, stateless instance} */
  public static WhitespaceCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  @Override
  public CharSequence normalize(CharSequence text) {
    return WHITESPACE.trim(WHITESPACE.collapse(text));
  }
}
