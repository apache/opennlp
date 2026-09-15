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

package opennlp.spellcheck.dictionary;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import opennlp.spellcheck.symspell.SymSpell;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.StringUtil;

/**
 * Loads plain-text frequency dictionaries into a {@link SymSpell} engine.
 *
 * <p>Two text formats are supported, both consumed line-by-line through an
 * {@link ObjectStream} (a {@link PlainTextByLineStream} over a caller-supplied
 * {@link InputStreamFactory}):</p>
 *
 * <ul>
 *   <li><b>unigram dictionary</b> &ndash; {@code word<sep>count} per line, fed to
 *       {@link SymSpell#add(String, long)};</li>
 *   <li><b>bigram dictionary</b> (optional) &ndash; {@code w1<sep>w2<sep>count} per line,
 *       fed to {@link SymSpell#addBigram(String, String, long)}.</li>
 * </ul>
 *
 * <p>Columns are separated by whitespace &ndash; a TAB or one or more spaces &ndash; so
 * the canonical space-delimited SymSpell reference dictionaries (e.g.
 * {@code frequency_dictionary_en_82_765.txt}) load as-is, as do TAB-delimited files.</p>
 *
 * <p>The loader is encoding-aware (UTF-8 by default) and tolerant of input noise: a
 * leading UTF-8 byte-order mark is stripped; blank lines, lines that are entirely
 * whitespace, and lines starting with {@code #} (comments) are skipped. A line that does
 * not match the expected shape (too few columns, unparsable count) is reported through
 * {@link MalformedDictionaryLineException}.</p>
 *
 * <p>This class performs only parsing and dispatch; it never mutates the engine's
 * configuration. Build the {@link SymSpell} with the desired {@code SymSpellConfig}
 * first, then load one or more dictionaries into it.</p>
 */
public final class FrequencyDictionaryLoader {

  /** The default character set used when none is supplied. */
  public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

  /** UTF-8 byte-order mark (U+FEFF); stripped if it leads a line. */
  private static final char BOM = (char) 0xFEFF;

  /** The first character of a comment line. */
  private static final char COMMENT_MARKER = '#';

  private static final String COUNT_NOT_DIGITS = "count must be ASCII digits with an optional sign";
  private static final String COUNT_NEGATIVE = "count must not be negative";
  private static final String COUNT_OUT_OF_RANGE = "count is out of range";

  private final Charset charset;

  /** Creates a loader using the {@linkplain #DEFAULT_CHARSET default UTF-8} charset. */
  public FrequencyDictionaryLoader() {
    this(DEFAULT_CHARSET);
  }

  /**
   * Creates a loader using the supplied charset.
   *
   * @param charset the character set used to decode the dictionary text; must not be
   *                {@code null}
   */
  public FrequencyDictionaryLoader(Charset charset) {
    this.charset = Objects.requireNonNull(charset, "charset must not be null");
  }

  /**
   * Loads a unigram frequency dictionary ({@code word<sep>count}) into {@code target}.
   *
   * @param target  the engine to populate; must not be {@code null}
   * @param factory the source of the dictionary text; must not be {@code null}
   * @return the number of dictionary entries that were read (after skipping blank and
   *     comment lines)
   * @throws IOException Thrown on IO errors or on a malformed line.
   */
  public long loadUnigrams(SymSpell target, InputStreamFactory factory) throws IOException {
    Objects.requireNonNull(target, "target must not be null");
    return readUnigrams(factory, target::add);
  }

  /**
   * Loads a bigram frequency dictionary ({@code w1<sep>w2<sep>count}) into {@code target}.
   *
   * @param target  the engine to populate; must not be {@code null}
   * @param factory the source of the dictionary text; must not be {@code null}
   * @return the number of bigram entries that were read (after skipping blank and
   *     comment lines)
   * @throws IOException Thrown on IO errors or on a malformed line.
   */
  public long loadBigrams(SymSpell target, InputStreamFactory factory) throws IOException {
    Objects.requireNonNull(target, "target must not be null");
    return readBigrams(factory, target::addBigram);
  }

  /**
   * Parses a unigram dictionary into the supplied accumulator, summing duplicate keys
   * (mirroring the engine's saturating count accumulation).
   *
   * @param factory the source of the dictionary text; must not be {@code null}
   * @param into     the {@code word -> count} accumulator to populate; must not be
   *                 {@code null}
   * @return the number of entries read
   * @throws IOException Thrown on IO errors or on a malformed line.
   */
  long parseUnigrams(InputStreamFactory factory, Map<String, Long> into) throws IOException {
    Objects.requireNonNull(into, "into must not be null");
    return readUnigrams(factory,
        (word, count) -> into.merge(word, count, FrequencyDictionaryLoader::saturatedAdd));
  }

  /**
   * Parses a bigram dictionary into the supplied accumulator (keyed by {@code "w1 w2"}),
   * summing duplicate keys.
   *
   * @param factory the source of the dictionary text; must not be {@code null}
   * @param into     the {@code "w1 w2" -> count} accumulator to populate; must not be
   *                 {@code null}
   * @return the number of entries read
   * @throws IOException Thrown on IO errors or on a malformed line.
   */
  long parseBigrams(InputStreamFactory factory, Map<String, Long> into) throws IOException {
    Objects.requireNonNull(into, "into must not be null");
    return readBigrams(factory,
        (w1, w2, count) -> into.merge(w1 + " " + w2, count,
            FrequencyDictionaryLoader::saturatedAdd));
  }

  private long readUnigrams(InputStreamFactory factory, UnigramSink sink) throws IOException {
    Objects.requireNonNull(factory, "factory must not be null");
    long read = 0;
    try (ObjectStream<String> lines = new PlainTextByLineStream(factory, charset)) {
      String line;
      long lineNo = 0;
      while ((line = lines.read()) != null) {
        lineNo++;
        final String content = stripBom(line);
        if (isSkippable(content)) {
          continue;
        }
        final String[] columns = splitColumns(content.strip());
        if (columns.length < 2) {
          throw new MalformedDictionaryLineException(lineNo, line, "expected 'word<sep>count'");
        }
        final long count = parseCount(columns[1], lineNo, line);
        sink.accept(columns[0], count);
        read++;
      }
    }
    return read;
  }

  private long readBigrams(InputStreamFactory factory, BigramSink sink) throws IOException {
    Objects.requireNonNull(factory, "factory must not be null");
    long read = 0;
    try (ObjectStream<String> lines = new PlainTextByLineStream(factory, charset)) {
      String line;
      long lineNo = 0;
      while ((line = lines.read()) != null) {
        lineNo++;
        final String content = stripBom(line);
        if (isSkippable(content)) {
          continue;
        }
        final String[] columns = splitColumns(content.strip());
        if (columns.length < 3) {
          throw new MalformedDictionaryLineException(lineNo, line, "expected 'w1<sep>w2<sep>count'");
        }
        final long count = parseCount(columns[2], lineNo, line);
        sink.accept(columns[0], columns[1], count);
        read++;
      }
    }
    return read;
  }

  /**
   * Splits {@code line} into columns on runs of TAB and space characters. Leading, trailing,
   * and repeated separators produce no empty column, so a line of only separators or an
   * empty line has no columns. Other whitespace, such as a no-break space or a vertical tab,
   * is part of a column.
   *
   * @param line The line to split. Must not be {@code null}.
   * @return The non-empty columns in order.
   */
  static String[] splitColumns(String line) {
    final List<String> columns = new ArrayList<>();
    int start = -1;
    for (int i = 0; i <= line.length(); i++) {
      if (i == line.length() || isColumnSeparator(line.charAt(i))) {
        if (start >= 0) {
          columns.add(line.substring(start, i));
          start = -1;
        }
      } else if (start < 0) {
        start = i;
      }
    }
    return columns.toArray(new String[0]);
  }

  /**
   * Tests whether {@code c} separates dictionary columns, which only a TAB or a space does.
   *
   * @param c The character to check.
   * @return {@code true} if {@code c} is a TAB or a space.
   */
  private static boolean isColumnSeparator(char c) {
    return c == '\t' || c == ' ';
  }

  private static String stripBom(String line) {
    if (!line.isEmpty() && line.charAt(0) == BOM) {
      return line.substring(1);
    }
    return line;
  }

  /**
   * Tests whether a line carries no entry: it is empty, consists of whitespace as
   * {@link StringUtil#isBlank(CharSequence)} defines it, or starts with {@code #}.
   *
   * @param line The line without its byte-order mark. Must not be {@code null}.
   * @return {@code true} if the line is to be skipped.
   */
  private static boolean isSkippable(String line) {
    if (StringUtil.isBlank(line)) {
      return true;
    }
    return line.charAt(0) == COMMENT_MARKER;
  }

  /**
   * Parses the count column: one or more ASCII digits, {@code 0} to {@code 9}, after an
   * optional {@code +} or {@code -}. Digits of other scripts, a decimal point, and an
   * exponent are malformed.
   *
   * @param raw The column text. Must not be {@code null}.
   * @param lineNo The 1-based line number, for the error message.
   * @param line The whole line, for the error message.
   * @return The count, zero or more.
   * @throws MalformedDictionaryLineException Thrown if the column is not such a number, is
   *         negative, or does not fit in a {@code long}.
   */
  private static long parseCount(String raw, long lineNo, String line) throws IOException {
    final String trimmed = raw.trim();
    final int digitsStart = !trimmed.isEmpty() && (trimmed.charAt(0) == '+' || trimmed.charAt(0) == '-')
        ? 1 : 0;
    if (digitsStart == trimmed.length()
        || StringUtil.endOfAsciiDigits(trimmed, digitsStart) != trimmed.length()) {
      throw new MalformedDictionaryLineException(lineNo, line, COUNT_NOT_DIGITS);
    }
    final long count;
    try {
      count = Long.parseLong(trimmed);
    } catch (NumberFormatException e) {
      throw new MalformedDictionaryLineException(lineNo, line, COUNT_OUT_OF_RANGE);
    }
    if (count < 0) {
      throw new MalformedDictionaryLineException(lineNo, line, COUNT_NEGATIVE);
    }
    return count;
  }

  private static long saturatedAdd(long a, long b) {
    final long sum = a + b;
    if (((a ^ sum) & (b ^ sum)) < 0) {
      return Long.MAX_VALUE;
    }
    return sum;
  }

  /** Receives a parsed unigram entry; may throw {@link IOException} from the consumer. */
  @FunctionalInterface
  private interface UnigramSink {
    void accept(String word, long count) throws IOException;
  }

  /** Receives a parsed bigram entry; may throw {@link IOException} from the consumer. */
  @FunctionalInterface
  private interface BigramSink {
    void accept(String w1, String w2, long count) throws IOException;
  }
}
