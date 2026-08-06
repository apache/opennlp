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

package opennlp.tools.formats;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import opennlp.tools.lemmatizer.DictionaryLemmatizer;

/**
 * Builds a {@link DictionaryLemmatizer} from a morfologik-format morphological dictionary: an
 * FSA5 or CFSA2 automaton (read by {@link FsaSequenceReader}) whose accepted byte sequences are
 * {@code surfaceForm SEP encodedBase SEP tag}, paired with the {@code .info} metadata that
 * declares the separator byte, the character encoding, and the base-form encoder.
 *
 * <p>This is a clean-room reader with no dependency on the morfologik library. The base form
 * (lemma) is stored relative to the surface form to save space; the four encoders are decoded
 * here. Each is a run of control bytes offset by {@code 'A'}, followed by literal bytes to
 * append:</p>
 * <ul>
 *   <li>{@code NONE}: the encoded bytes are the base form verbatim.</li>
 *   <li>{@code SUFFIX} ({@code K}): drop {@code K} bytes from the end of the form, then append.</li>
 *   <li>{@code PREFIX} ({@code P},{@code K}): drop {@code P} from the front and {@code K} from the
 *       end of the form, then append.</li>
 *   <li>{@code INFIX} ({@code I},{@code L},{@code K}): drop {@code L} bytes at offset {@code I} and
 *       {@code K} from the end of the form, then append.</li>
 * </ul>
 *
 * <p>Surface forms are lower-cased on load, because {@link DictionaryLemmatizer} lower-cases the
 * queried token before lookup. Dictionary data is supplied by the caller and never bundled.</p>
 */
public final class MorfologikDictionaryReader {

  /** The base-form encoder declared by a dictionary's {@code fsa.dict.encoder}. */
  public enum BaseFormEncoding {

    /** The encoded bytes are the base form verbatim. */
    NONE,

    /** The base form drops trailing bytes of the surface form, then appends the encoded rest. */
    SUFFIX,

    /** As {@link #SUFFIX}, and leading bytes of the surface form are dropped too. */
    PREFIX,

    /** As {@link #SUFFIX}, and a run of bytes inside the surface form is dropped too. */
    INFIX
  }

  private static final int OFFSET = 'A';
  private static final String KEY_SEPARATOR = "fsa.dict.separator";
  private static final String KEY_ENCODING = "fsa.dict.encoding";
  private static final String KEY_ENCODER = "fsa.dict.encoder";

  private static final String FIELD_SEPARATOR = "\t";
  private static final String LEMMA_SEPARATOR = "#";

  /** Not instantiable. */
  private MorfologikDictionaryReader() {
  }

  /**
   * Reads a morfologik CFSA2 dictionary into a {@link DictionaryLemmatizer} using an explicit
   * separator, encoder, and charset.
   *
   * @param dictionary The CFSA2 automaton, referenced by an open {@link InputStream}. Must not be
   *                   {@code null}.
   * @param separator  The byte separating the form, encoded base, and tag fields.
   * @param encoding   The base-form encoder. Must not be {@code null}.
   * @param charset    The character encoding of the dictionary bytes. Must not be {@code null}.
   * @return A {@link DictionaryLemmatizer} over the decoded entries.
   * @throws IllegalArgumentException Thrown if {@code dictionary}, {@code encoding}, or
   *                                  {@code charset} is {@code null}.
   * @throws IOException Thrown on IO errors, if the stream is not a CFSA2 automaton, or if an
   *                     entry cannot be split into a form and encoded base.
   */
  public static DictionaryLemmatizer read(InputStream dictionary, byte separator,
      BaseFormEncoding encoding, Charset charset) throws IOException {
    if (dictionary == null) {
      throw new IllegalArgumentException("dictionary must not be null");
    }
    if (encoding == null) {
      throw new IllegalArgumentException("encoding must not be null");
    }
    if (charset == null) {
      throw new IllegalArgumentException("charset must not be null");
    }

    final FsaSequenceReader automaton = FsaSequenceReader.read(dictionary);
    final Map<String, LinkedHashSet<String>> entries = new LinkedHashMap<>();
    try {
      automaton.forEachSequence(
          sequence -> addEntry(sequence, separator, encoding, charset, entries));
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }

    final StringBuilder adapted = new StringBuilder();
    for (final Map.Entry<String, LinkedHashSet<String>> entry : entries.entrySet()) {
      adapted.append(entry.getKey())
          .append(FIELD_SEPARATOR)
          .append(String.join(LEMMA_SEPARATOR, entry.getValue()))
          .append('\n');
    }
    final byte[] bytes = adapted.toString().getBytes(StandardCharsets.UTF_8);
    return new DictionaryLemmatizer(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8);
  }

  /**
   * Reads a morfologik CFSA2 dictionary into a {@link DictionaryLemmatizer}, taking the separator,
   * charset, and encoder from the dictionary's {@code .info} metadata.
   *
   * @param dictionary The CFSA2 automaton, referenced by an open {@link InputStream}. Must not be
   *                   {@code null}.
   * @param info       The {@code .info} metadata properties, referenced by an open
   *                   {@link InputStream}. Must not be {@code null} and must declare
   *                   {@code fsa.dict.separator}, {@code fsa.dict.encoding}, and
   *                   {@code fsa.dict.encoder}.
   * @return A {@link DictionaryLemmatizer} over the decoded entries.
   * @throws IllegalArgumentException Thrown if an argument is {@code null} or a required metadata
   *                                  key is missing or invalid.
   * @throws IOException Thrown on IO errors or invalid dictionary content.
   */
  public static DictionaryLemmatizer read(InputStream dictionary, InputStream info)
      throws IOException {
    if (info == null) {
      throw new IllegalArgumentException("info must not be null");
    }
    final Properties properties = new Properties();
    properties.load(info);

    final String separator = required(properties, KEY_SEPARATOR);
    if (separator.length() != 1) {
      throw new IllegalArgumentException(KEY_SEPARATOR + " must be a single character");
    }
    final Charset charset = Charset.forName(required(properties, KEY_ENCODING));
    final BaseFormEncoding encoding = BaseFormEncoding.valueOf(
        required(properties, KEY_ENCODER).toUpperCase(Locale.ROOT));
    return read(dictionary, (byte) separator.charAt(0), encoding, charset);
  }

  /**
   * Reads a metadata value that the dictionary must declare.
   *
   * @param properties The parsed {@code .info} metadata.
   * @param key        The metadata key to read.
   * @return The declared value.
   * @throws IllegalArgumentException Thrown if the key is not declared.
   */
  private static String required(Properties properties, String key) {
    final String value = properties.getProperty(key);
    if (value == null) {
      throw new IllegalArgumentException("missing required metadata key: " + key);
    }
    return value;
  }

  /**
   * Splits one accepted sequence into form, base form, and tag, and records it.
   *
   * @param sequence  The accepted byte sequence, the fields joined by {@code separator}.
   * @param separator The byte separating the fields.
   * @param encoding  The encoder the base form is stored with.
   * @param charset   The character encoding of the dictionary bytes.
   * @param entries   The entries collected so far, keyed by form and tag; updated in place.
   * @throws UncheckedIOException Thrown if the sequence carries no separator or its base form
   *                              cannot be decoded.
   */
  private static void addEntry(byte[] sequence, byte separator, BaseFormEncoding encoding,
      Charset charset, Map<String, LinkedHashSet<String>> entries) {
    final int firstSeparator = indexOf(sequence, separator, 0);
    if (firstSeparator < 0) {
      throw new UncheckedIOException(new IOException(
          "morfologik entry has no separator: " + new String(sequence, charset)));
    }
    final int secondSeparator = indexOf(sequence, separator, firstSeparator + 1);
    final int baseEnd = secondSeparator < 0 ? sequence.length : secondSeparator;

    final byte[] form = slice(sequence, 0, firstSeparator);
    final byte[] encodedBase = slice(sequence, firstSeparator + 1, baseEnd);
    final String tag = secondSeparator < 0 ? ""
        : new String(sequence, secondSeparator + 1, sequence.length - secondSeparator - 1, charset);

    final byte[] base;
    try {
      base = decodeBaseForm(form, encodedBase, encoding);
    } catch (IllegalArgumentException e) {
      throw new UncheckedIOException(new IOException(
          "malformed morfologik entry: " + new String(sequence, charset), e));
    }

    final String key = new String(form, charset).toLowerCase() + FIELD_SEPARATOR + tag;
    entries.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(new String(base, charset));
  }

  /**
   * Recovers a base form from a surface form and its encoded representation.
   *
   * @param form     The surface form bytes.
   * @param encoded  The encoded base bytes: control bytes followed by literal bytes to append.
   * @param encoding The encoder that produced {@code encoded}.
   * @return The decoded base form bytes.
   * @throws IllegalArgumentException Thrown if {@code encoded} is too short for the encoder or the
   *                                  control bytes address positions outside {@code form}.
   */
  static byte[] decodeBaseForm(byte[] form, byte[] encoded, BaseFormEncoding encoding) {
    switch (encoding) {
      case NONE:
        return encoded.clone();
      case SUFFIX: {
        require(encoded, 1, encoding);
        final int keep = bounded(form.length - control(encoded[0]), form);
        return join(form, 0, keep, encoded, 1);
      }
      case PREFIX: {
        require(encoded, 2, encoding);
        final int start = bounded(control(encoded[0]), form);
        final int end = bounded(form.length - control(encoded[1]), form);
        return join(form, Math.min(start, end), end, encoded, 2);
      }
      case INFIX: {
        require(encoded, 3, encoding);
        final int cut = bounded(control(encoded[0]), form);
        final int resume = bounded(cut + control(encoded[1]), form);
        final int end = bounded(form.length - control(encoded[2]), form);
        return join3(form, cut, Math.max(resume, cut), Math.max(end, resume), encoded, 3);
      }
      default:
        throw new IllegalArgumentException("unknown encoder: " + encoding);
    }
  }

  /**
   * Reads one control byte as the offset it encodes.
   *
   * @param b The control byte.
   * @return The encoded offset, the byte value less {@code 'A'}.
   */
  private static int control(byte b) {
    return (b & 0xff) - OFFSET;
  }

  /**
   * Checks that an encoded base form carries all the control bytes its encoder needs.
   *
   * @param encoded     The encoded base bytes.
   * @param prefixBytes The number of control bytes the encoder needs.
   * @param encoding    The encoder, named in the failure message.
   * @throws IllegalArgumentException Thrown if fewer bytes are present.
   */
  private static void require(byte[] encoded, int prefixBytes, BaseFormEncoding encoding) {
    if (encoded.length < prefixBytes) {
      throw new IllegalArgumentException(
          encoding + " encoded base needs at least " + prefixBytes + " control byte(s)");
    }
  }

  /**
   * Checks that a decoded offset addresses a position within a surface form.
   *
   * @param index The offset to check.
   * @param form  The surface form bytes.
   * @return The offset itself.
   * @throws IllegalArgumentException Thrown if the offset lies outside {@code form}.
   */
  private static int bounded(int index, byte[] form) {
    if (index < 0 || index > form.length) {
      throw new IllegalArgumentException(
          "encoded base addresses byte " + index + " outside a form of length " + form.length);
    }
    return index;
  }

  /**
   * Joins one run of the surface form with the literal tail of an encoded base form.
   *
   * @param form       The surface form bytes.
   * @param from       The first byte of the run to keep.
   * @param to         The byte after the run to keep.
   * @param encoded    The encoded base bytes.
   * @param appendFrom The first literal byte of {@code encoded}, past its control bytes.
   * @return The decoded base form bytes.
   */
  private static byte[] join(byte[] form, int from, int to, byte[] encoded, int appendFrom) {
    final int kept = to - from;
    final int appended = encoded.length - appendFrom;
    final byte[] out = new byte[kept + appended];
    System.arraycopy(form, from, out, 0, kept);
    System.arraycopy(encoded, appendFrom, out, kept, appended);
    return out;
  }

  /**
   * Joins two runs of the surface form, the second past a dropped infix, with the literal tail of
   * an encoded base form.
   *
   * @param form       The surface form bytes.
   * @param headEnd    The byte after the leading run to keep, which starts at zero.
   * @param tailFrom   The first byte of the trailing run to keep.
   * @param tailEnd    The byte after the trailing run to keep.
   * @param encoded    The encoded base bytes.
   * @param appendFrom The first literal byte of {@code encoded}, past its control bytes.
   * @return The decoded base form bytes.
   */
  private static byte[] join3(byte[] form, int headEnd, int tailFrom, int tailEnd,
      byte[] encoded, int appendFrom) {
    final int tail = tailEnd - tailFrom;
    final int appended = encoded.length - appendFrom;
    final byte[] out = new byte[headEnd + tail + appended];
    System.arraycopy(form, 0, out, 0, headEnd);
    System.arraycopy(form, tailFrom, out, headEnd, tail);
    System.arraycopy(encoded, appendFrom, out, headEnd + tail, appended);
    return out;
  }

  /**
   * Finds the next occurrence of a byte.
   *
   * @param array The bytes to search.
   * @param value The byte to find.
   * @param from  The index to start at.
   * @return The index of the first occurrence at or after {@code from}, or {@code -1} when absent.
   */
  private static int indexOf(byte[] array, byte value, int from) {
    for (int i = from; i < array.length; i++) {
      if (array[i] == value) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Copies a run of bytes.
   *
   * @param array The bytes to copy from.
   * @param from  The first byte to copy.
   * @param to    The byte after the last one to copy.
   * @return The copied run.
   */
  private static byte[] slice(byte[] array, int from, int to) {
    final byte[] out = new byte[to - from];
    System.arraycopy(array, from, out, 0, to - from);
    return out;
  }
}
