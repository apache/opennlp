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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/**
 * Computes the Unicode confusable <em>skeleton</em> of text, following the algorithm in
 * <a href="https://www.unicode.org/reports/tr39/">UTS #39</a> (Unicode Security Mechanisms). Two
 * strings are confusable, for example Latin {@code "paypal"} and a version using Cyrillic
 * lookalikes, exactly when their skeletons are equal.
 *
 * <p>The mapping is loaded once from the {@code confusables.txt} resource of the Unicode security
 * data (parsed with simple cursor scanning, no regular expression). The skeleton of a string is
 * {@code NFD(map(NFD(s)))}: decompose, replace each code point with its prototype, and decompose
 * again. This changes length and offsets, so it belongs to the derived, matching-only form rather
 * than to any offset-preserving transform.</p>
 */
public final class Confusables {

  private static final String RESOURCE = "confusables.txt";

  // Maps a single confusable code point to its prototype sequence (one or more code points).
  private static final Map<Integer, String> PROTOTYPES = load();

  private Confusables() {
  }

  private static Map<Integer, String> load() {
    final Map<Integer, String> map = new HashMap<>(12000);
    try (InputStream in = Confusables.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing confusables data resource: " + RESOURCE);
      }
      try (BufferedReader reader =
               new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          final int hash = line.indexOf('#');
          final String content = (hash < 0 ? line : line.substring(0, hash)).strip();
          if (content.isEmpty()) {
            continue;
          }
          final int firstSemicolon = content.indexOf(';');
          final int secondSemicolon = content.indexOf(';', firstSemicolon + 1);
          if (firstSemicolon < 0 || secondSemicolon < 0) {
            continue;
          }
          final int source = Integer.parseInt(content.substring(0, firstSemicolon).strip(), 16);
          final String target = content.substring(firstSemicolon + 1, secondSemicolon).strip();
          final StringBuilder prototype = new StringBuilder();
          // Scan the whitespace-delimited hex tokens by hand to honor the no-regex contract and
          // avoid compiling a Pattern for every one of the ~10k lines during static init.
          final int targetLength = target.length();
          int pos = 0;
          while (pos < targetLength) {
            while (pos < targetLength && target.charAt(pos) <= ' ') {
              pos++;
            }
            int end = pos;
            while (end < targetLength && target.charAt(end) > ' ') {
              end++;
            }
            if (end > pos) {
              prototype.appendCodePoint(Integer.parseInt(target.substring(pos, end), 16));
            }
            pos = end;
          }
          map.put(source, prototype.toString());
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to read confusables data resource " + RESOURCE, e);
    }
    return map;
  }

  /**
   * Returns the confusable skeleton of {@code text}: {@code NFD(map(NFD(text)))} where {@code map}
   * replaces each code point with its UTS&#160;#39 prototype. The skeleton is for comparison only;
   * it is not human-readable text and does not preserve offsets.
   *
   * @param text The text to reduce.
   * @return The skeleton.
   */
  public static String skeleton(CharSequence text) {
    final String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
    final StringBuilder mapped = new StringBuilder(decomposed.length());
    for (int i = 0; i < decomposed.length(); ) {
      final int codePoint = decomposed.codePointAt(i);
      i += Character.charCount(codePoint);
      final String prototype = PROTOTYPES.get(codePoint);
      if (prototype != null) {
        mapped.append(prototype);
      } else {
        mapped.appendCodePoint(codePoint);
      }
    }
    return Normalizer.normalize(mapped, Normalizer.Form.NFD);
  }

  /**
   * {@return whether {@code left} and {@code right} are confusable} They are confusable when their
   * {@linkplain #skeleton(CharSequence) skeletons} are equal.
   *
   * @param left  The first string.
   * @param right The second string.
   */
  public static boolean confusable(CharSequence left, CharSequence right) {
    return skeleton(left).equals(skeleton(right));
  }
}
