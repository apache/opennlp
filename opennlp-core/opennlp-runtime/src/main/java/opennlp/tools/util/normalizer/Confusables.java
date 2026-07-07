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
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Computes the Unicode confusable <em>skeleton</em> of text, following the skeleton algorithm
 * defined in <a href="https://www.unicode.org/reports/tr39/">UTS #39</a> (Unicode Security
 * Mechanisms). Two
 * strings are confusable, for example Latin {@code "paypal"} and a version using Cyrillic
 * lookalikes, exactly when their skeletons are equal.
 *
 * <p>The mapping is loaded once from the {@code confusables.txt} resource of the Unicode security
 * data (parsed with simple cursor scanning, no regular expression). The skeleton of a string is
 * {@code NFD(map(NFD(s)))}: decompose, replace each code point with its prototype, and decompose
 * again. This changes length and offsets, so it belongs to the derived, matching-only form rather
 * than to any offset-preserving transform.</p>
 *
 * <p>This implements only the skeleton transform and the confusable-detection test built on
 * skeleton equality. The other mechanisms defined in UTS&#160;#39, such as identifier
 * restriction levels, mixed-script and whole-script confusable detection, and the bidirectional
 * skeleton, are out of scope; the skeleton here is a comparison form, not a security-grade
 * conformance claim for the full report.</p>
 */
public final class Confusables {

  private static final String RESOURCE = "confusables.txt";

  // The prototype map plus a BitSet over its keys, so the hot no-mapping test is one O(1) bit read
  // with no Integer boxing. Immutable after construction; published through the volatile field.
  private record Data(Map<Integer, String> prototypes, BitSet keys) {
  }

  private static volatile Data data;

  private Confusables() {
  }

  private static Data data() {
    Data d = data;
    if (d == null) {
      synchronized (Confusables.class) {
        d = data;
        if (d == null) {
          d = load();
          data = d;
        }
      }
    }
    return d;
  }

  private static Data load() {
    try (InputStream in = Confusables.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing confusables data resource: " + RESOURCE);
      }
      final Map<Integer, String> prototypes = parse(in);
      final BitSet keys = new BitSet();
      for (final int codePoint : prototypes.keySet()) {
        keys.set(codePoint);
      }
      return new Data(prototypes, keys);
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to read confusables data resource " + RESOURCE, e);
    }
  }

  // Package-private so the malformed-data handling can be exercised without the bundled resource.
  static Map<Integer, String> parse(InputStream in) throws IOException {
    final Map<Integer, String> map = new HashMap<>();
    try (BufferedReader reader =
             new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        final int hash = line.indexOf('#');
        final String content = (hash < 0 ? line : line.substring(0, hash)).strip();
        if (content.isEmpty()) {
          continue;
        }
        final int firstSemicolon = content.indexOf(';');
        final int secondSemicolon = content.indexOf(';', firstSemicolon + 1);
        if (firstSemicolon < 0 || secondSemicolon < 0) {
          // A present-but-structurally-wrong line (fewer than two ';') is a hard error, like the
          // malformed-hex path below and the sibling loaders -- never silently dropped.
          throw new IllegalArgumentException("Malformed confusables data in " + RESOURCE + " at line "
              + lineNumber + ": " + content);
        }
        try {
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
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("Malformed confusables data in " + RESOURCE + " at line "
              + lineNumber + ": " + content, e);
        }
      }
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
    final Data data = data();
    final BitSet keys = data.keys();

    // Fast path: when no code point of the text is a prototype key and the text is already in
    // NFD form (trivially true for pure ASCII, which never decomposes), the skeleton is the text
    // itself: NFD(text) == text, the map pass changes nothing because no key occurs, and the
    // second NFD of an unchanged NFD string is again the identity. This skips both Normalizer
    // passes and all allocation for the common clean-token case. Text that fails either test
    // (a key, or a non-ASCII code point in un-normalized form) takes the full UTS #39 path below,
    // whose two NFD passes remain required.
    final int length = text.length();
    boolean asciiOnly = true;
    boolean anyKey = false;
    int i = 0;
    while (i < length) {
      final char c = text.charAt(i);
      final int codePoint;
      if (!Character.isHighSurrogate(c)) {
        codePoint = c;
        i++;
      } else {
        codePoint = Character.codePointAt(text, i);
        i += Character.charCount(codePoint);
      }
      if (codePoint >= 0x80) {
        asciiOnly = false;
      }
      if (keys.get(codePoint)) {
        anyKey = true;
        break;
      }
    }
    if (!anyKey && (asciiOnly || Normalizer.isNormalized(text, Normalizer.Form.NFD))) {
      return text.toString();
    }

    final Map<Integer, String> map = data.prototypes();
    final String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
    final StringBuilder mapped = new StringBuilder(decomposed.length());
    for (int j = 0; j < decomposed.length(); ) {
      final int codePoint = decomposed.codePointAt(j);
      j += Character.charCount(codePoint);
      // The BitSet pre-filter keeps the common miss free of Integer boxing; only an actual key
      // pays for the boxed map lookup.
      final String prototype = keys.get(codePoint) ? map.get(codePoint) : null;
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
