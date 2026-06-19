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
 * A {@link CharSequenceNormalizer} that expands the ellipsis and leader characters to ASCII dots:
 * the horizontal ellipsis ({@code U+2026}) to {@code "..."} and the two-dot leader
 * ({@code U+2025}) to {@code ".."}.
 *
 * <p>Scanning is a single O(1)-per-code-point cursor pass with no regular expression. ASCII dot
 * runs are left unchanged.</p>
 */
public class EllipsisCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 1L;

  private static final EllipsisCharSequenceNormalizer INSTANCE =
      new EllipsisCharSequenceNormalizer();

  /** {@return the shared, stateless instance} */
  public static EllipsisCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  @Override
  public CharSequence normalize(CharSequence text) {
    final StringBuilder out = new StringBuilder(text.length());
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      final String mapped = switch (codePoint) {
        case 0x2026 -> "...";  // horizontal ellipsis
        case 0x2025 -> "..";   // two dot leader
        default -> null;
      };
      if (mapped != null) {
        out.append(mapped);
      } else {
        out.appendCodePoint(codePoint);
      }
      i += Character.charCount(codePoint);
    }
    return out.toString();
  }
}
