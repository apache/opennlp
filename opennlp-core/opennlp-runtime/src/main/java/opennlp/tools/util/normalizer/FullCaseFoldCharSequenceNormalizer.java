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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.util.ArgumentChecks;
import opennlp.tools.util.StringUtil;

/**
 * A {@link CharSequenceNormalizer} that applies Unicode full case folding for case-insensitive
 * matching, as defined by the Default Case Algorithms in
 * <a href="https://www.unicode.org/versions/latest/core-spec/chapter-3/">Section 3.13 of the
 * Unicode Standard</a>, using the bundled {@code CaseFolding.txt} data of the Unicode Character
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
 * <p>The {@code S} simple and {@code T} Turkic status mappings are excluded, so the Turkish and
 * Azerbaijani dotless-i rule is not applied here. Input is expected in NFC: the fold matches
 * precomposed code points, so decomposed sequences pass through unchanged.</p>
 *
 * <p>The folding table is loaded on first use. If the bundled {@code CaseFolding.txt} is missing
 * or cannot be read, every normalize call throws {@link IllegalStateException} or
 * {@link UncheckedIOException}.</p>
 */
public final class FullCaseFoldCharSequenceNormalizer implements OffsetAwareNormalizer {

  private static final long serialVersionUID = 4520210518330612934L;

  private static final String RESOURCE = "CaseFolding.txt";

  /** Field separator in {@code CaseFolding.txt} ({@code <code>; <status>; <mapping>;}). */
  private static final char FIELD_SEPARATOR = ';';

  /**
   * Separates hex code points inside a multi-character mapping field. The Unicode
   * {@code CaseFolding.txt} format uses ASCII space ({@code U+0020}), not a general whitespace
   * class.
   */
  private static final char MAPPING_CODE_POINT_SEPARATOR = ' ';

  /** {@code CaseFolding.txt} status {@code C}: common mapping shared by simple and full folding. */
  private static final String STATUS_COMMON = "C";

  /** {@code CaseFolding.txt} status {@code F}: full mapping that may expand string length. */
  private static final String STATUS_FULL = "F";

  /** {@code CaseFolding.txt} status {@code S}: simple mapping; skipped by this full fold. */
  private static final String STATUS_SIMPLE = "S";

  /** {@code CaseFolding.txt} status {@code T}: Turkic mapping; skipped by this full fold. */
  private static final String STATUS_TURKIC = "T";

  /**
   * Maps a source code point to its full case folding (one or more code points), for the C and F
   * status rows of {@code CaseFolding.txt}. Loaded on first use.
   */
  private static final BundledUnicodeData.Lazy<Map<Integer, String>> FOLDINGS =
      new BundledUnicodeData.Lazy<>(FullCaseFoldCharSequenceNormalizer::initFoldings);

  private static final FullCaseFoldCharSequenceNormalizer INSTANCE =
      new FullCaseFoldCharSequenceNormalizer();

  /** Creates the singleton; use {@link #getInstance()}. */
  private FullCaseFoldCharSequenceNormalizer() {
  }

  /** {@return the shared, stateless instance} */
  public static FullCaseFoldCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException if {@code text} is {@code null}.
   * @throws IllegalStateException if the bundled {@code CaseFolding.txt} resource is missing.
   * @throws UncheckedIOException if the bundled {@code CaseFolding.txt} resource cannot be read.
   */
  @Override
  public CharSequence normalize(CharSequence text) {
    ArgumentChecks.requireNonNullArg(text, "text");
    return CharClass.substitute(text, FOLDINGS.get()::get);
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException if {@code text} is {@code null}.
   * @throws IllegalStateException if the bundled {@code CaseFolding.txt} resource is missing.
   * @throws UncheckedIOException if the bundled {@code CaseFolding.txt} resource cannot be read.
   */
  @Override
  public AlignedText normalizeAligned(CharSequence text) {
    ArgumentChecks.requireNonNullArg(text, "text");
    return CharClass.substituteAligned(text, FOLDINGS.get()::get);
  }

  /**
   * {@return the folding table parsed from the bundled {@code CaseFolding.txt} resource}
   *
   * @throws IllegalStateException if the resource is missing.
   * @throws UncheckedIOException if the resource cannot be read.
   */
  private static Map<Integer, String> initFoldings() {
    return Map.copyOf(BundledUnicodeData.load(FullCaseFoldCharSequenceNormalizer.class, RESOURCE,
        "case folding", FullCaseFoldCharSequenceNormalizer::parse));
  }

  /**
   * Parses the full-folding mappings, the {@code C} (common) and {@code F} (full) status rows of
   * {@code CaseFolding.txt}. The {@code S} (simple) and {@code T} (Turkic) status rows are
   * deliberately skipped so the fold stays language-neutral and full rather than simple.
   * Package-private so the malformed-data handling can be exercised without the bundled resource.
   *
   * @param in the stream to parse, in {@code CaseFolding.txt} format. Must not be {@code null}.
   * @return the mapping from source code point to its full case folding.
   * @throws IOException if the stream cannot be read.
   * @throws IllegalArgumentException if the data is malformed.
   */
  static Map<Integer, String> parse(InputStream in) throws IOException {
    final Map<Integer, String> map = new HashMap<>();
    BundledUnicodeData.forEachContentLine(in, (content, lineNumber) -> {
      final String[] fields = StringUtil.split(content, FIELD_SEPARATOR);
      if (fields.length < 3) {
        throw new IllegalArgumentException("Malformed case folding data in " + RESOURCE
            + " at line " + lineNumber + ": " + content);
      }
      final String status = fields[1].strip();
      if (STATUS_SIMPLE.equals(status) || STATUS_TURKIC.equals(status)) {
        // Simple and Turkic mappings are recognized but deliberately not part of the full fold.
        return;
      }
      if (!STATUS_COMMON.equals(status) && !STATUS_FULL.equals(status)) {
        // An unrecognized status is not a known-and-skipped case (S/T above); treat it as
        // corruption rather than silently dropping data.
        throw new IllegalArgumentException("Malformed case folding data in " + RESOURCE
            + " at line " + lineNumber + ": unrecognized status '" + status + "' in: " + content);
      }
      try {
        final int source = HexCodePoints.parseCodePoint(fields[0].strip());
        map.put(source, HexCodePoints.decodeSequence(fields[2].strip(),
            MAPPING_CODE_POINT_SEPARATOR));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Malformed case folding data in " + RESOURCE
            + " at line " + lineNumber + ": " + content, e);
      }
    });
    return map;
  }
}
