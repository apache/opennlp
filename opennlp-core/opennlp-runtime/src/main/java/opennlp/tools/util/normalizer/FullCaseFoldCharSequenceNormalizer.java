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
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link CharSequenceNormalizer} that applies Unicode full case folding (UTS&#160;#21) for
 * case-insensitive matching, using the bundled {@code CaseFolding.txt} data of the Unicode Character
 * Database.
 *
 * <p>Unlike {@link CaseFoldCharSequenceNormalizer}, which lower cases with
 * {@link java.util.Locale#ROOT}, this applies the full case foldings (the {@code C} common and
 * {@code F} full status mappings), including the expanding folds that plain lower casing does not
 * perform: the sharp s (U+00DF) to {@code ss}, the Latin ligatures (for example U+FB00 to
 * {@code ff}), and the Greek and Armenian multi-character folds. It is therefore an expanding,
 * offset-changing transform, so it is offset-aware: {@link #normalizeAligned(CharSequence)} reports
 * the {@link Alignment} from the folded text back to the input. A single cursor pass with no regular
 * expression.</p>
 *
 * <p>The {@code S} simple and {@code T} Turkic mappings are deliberately excluded, so the fold is the
 * language-neutral one (the Turkish dotless-i rule is not applied here). The fold matches precomposed
 * code points, so apply NFC composition first if the input may contain decomposed forms. Inside an
 * offset-aware pipeline that precondition cannot be established by a preceding rung, because NFC
 * reports no offsets and {@code TextNormalizer.Builder.buildAligned()} rejects it; feed the aligned
 * pipeline composed text, or accept that decomposed sequences pass through this fold unchanged.</p>
 */
public final class FullCaseFoldCharSequenceNormalizer implements OffsetAwareNormalizer {

  private static final long serialVersionUID = 4520210518330612934L;

  private static final String RESOURCE = "CaseFolding.txt";

  // Maps a source code point to its full case folding (one or more code points), for the C and F
  // status rows of CaseFolding.txt.
  private static volatile Map<Integer, String> foldings;

  private static final FullCaseFoldCharSequenceNormalizer INSTANCE =
      new FullCaseFoldCharSequenceNormalizer();

  private FullCaseFoldCharSequenceNormalizer() {
  }

  /** {@return the shared, stateless instance} */
  public static FullCaseFoldCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  @Override
  public CharSequence normalize(CharSequence text) {
    // Resolve the table once per call rather than once per code point, so the volatile read and
    // double-checked-lock guard is not repeated in the hot per-character loop (CharClass.substitute
    // calls the substitution function once per code point in text).
    final Map<Integer, String> table = foldings();
    return CharClass.substitute(text, table::get);
  }

  @Override
  public AlignedText normalizeAligned(CharSequence text) {
    final Map<Integer, String> table = foldings();
    return CharClass.substituteAligned(text, table::get);
  }

  private static Map<Integer, String> foldings() {
    Map<Integer, String> map = foldings;
    if (map == null) {
      synchronized (FullCaseFoldCharSequenceNormalizer.class) {
        map = foldings;
        if (map == null) {
          map = load();
          foldings = map;
        }
      }
    }
    return map;
  }

  private static Map<Integer, String> load() {
    try (InputStream in = FullCaseFoldCharSequenceNormalizer.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing case folding data resource: " + RESOURCE);
      }
      return parse(in);
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to read case folding data resource " + RESOURCE, e);
    }
  }

  // Package-private so the malformed-data handling can be exercised without the bundled resource.
  // Loads the full-folding mappings: the C (common) and F (full) status rows of CaseFolding.txt. The
  // S (simple) and T (Turkic) status rows are deliberately skipped so the fold stays language-neutral
  // and full rather than simple.
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
        final String[] fields = content.split(";");
        if (fields.length < 3) {
          throw new IllegalArgumentException("Malformed case folding data in " + RESOURCE
              + " at line " + lineNumber + ": " + content);
        }
        final String status = fields[1].strip();
        if ("S".equals(status) || "T".equals(status)) {
          // Simple and Turkic mappings are recognized but deliberately not part of the full fold.
          continue;
        }
        if (!"C".equals(status) && !"F".equals(status)) {
          // An unrecognized status is not a known-and-skipped case (S/T above); treat it as
          // corruption rather than silently dropping data, the same fail-loud contract as the
          // sibling loaders (Confusables, WordBreakProperty).
          throw new IllegalArgumentException("Malformed case folding data in " + RESOURCE
              + " at line " + lineNumber + ": unrecognized status '" + status + "' in: " + content);
        }
        try {
          final int source = Integer.parseInt(fields[0].strip(), 16);
          final StringBuilder target = new StringBuilder();
          for (final String hex : fields[2].strip().split(" ")) {
            target.appendCodePoint(Integer.parseInt(hex, 16));
          }
          map.put(source, target.toString());
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("Malformed case folding data in " + RESOURCE
              + " at line " + lineNumber + ": " + content, e);
        }
      }
    }
    return map;
  }
}
