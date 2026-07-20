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
 * <p>Dictionary data is supplied by the caller and never bundled. Thread safety is implementation
 * specific.</p>
 */
public final class MorfologikDictionaryReader {

  /** The base-form encoder declared by a dictionary's {@code fsa.dict.encoder}. */
  public enum BaseFormEncoding {
    NONE, SUFFIX, PREFIX, INFIX
  }

  private static final int OFFSET = 'A';
  private static final String KEY_SEPARATOR = "fsa.dict.separator";
  private static final String KEY_ENCODING = "fsa.dict.encoding";
  private static final String KEY_ENCODER = "fsa.dict.encoder";

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
   * @throws IllegalArgumentException if {@code dictionary}, {@code encoding}, or {@code charset}
   *                                  is {@code null}.
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
      automaton.forEachSequence(sequence -> addEntry(sequence, separator, encoding, charset, entries));
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }

    final StringBuilder adapted = new StringBuilder();
    for (final Map.Entry<String, LinkedHashSet<String>> entry : entries.entrySet()) {
      adapted.append(entry.getKey())
          .append('\t')
          .append(String.join("#", entry.getValue()))
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
   * @throws IllegalArgumentException if an argument is {@code null} or a required metadata key is
   *                                  missing or invalid.
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
    final BaseFormEncoding encoding =
        BaseFormEncoding.valueOf(required(properties, KEY_ENCODER).toUpperCase());
    return read(dictionary, (byte) separator.charAt(0), encoding, charset);
  }

  private static String required(Properties properties, String key) {
    final String value = properties.getProperty(key);
    if (value == null) {
      throw new IllegalArgumentException("missing required metadata key: " + key);
    }
    return value;
  }

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

    final String key = new String(form, charset).toLowerCase() + '\t' + tag;
    entries.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(new String(base, charset));
  }

  /**
   * Recovers a base form from a surface form and its encoded representation.
   *
   * @param form     The surface form bytes.
   * @param encoded  The encoded base bytes: control bytes followed by literal bytes to append.
   * @param encoding The encoder that produced {@code encoded}.
   * @return The decoded base form bytes.
   * @throws IllegalArgumentException if {@code encoded} is too short for the encoder or the control
   *                                  bytes address positions outside {@code form}.
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

  private static int control(byte b) {
    return (b & 0xff) - OFFSET;
  }

  private static void require(byte[] encoded, int prefixBytes, BaseFormEncoding encoding) {
    if (encoded.length < prefixBytes) {
      throw new IllegalArgumentException(
          encoding + " encoded base needs at least " + prefixBytes + " control byte(s)");
    }
  }

  private static int bounded(int index, byte[] form) {
    if (index < 0 || index > form.length) {
      throw new IllegalArgumentException(
          "encoded base addresses byte " + index + " outside a form of length " + form.length);
    }
    return index;
  }

  private static byte[] join(byte[] form, int from, int to, byte[] encoded, int appendFrom) {
    final int kept = to - from;
    final int appended = encoded.length - appendFrom;
    final byte[] out = new byte[kept + appended];
    System.arraycopy(form, from, out, 0, kept);
    System.arraycopy(encoded, appendFrom, out, kept, appended);
    return out;
  }

  private static byte[] join3(byte[] form, int headEnd, int tailFrom, int tailEnd,
      byte[] encoded, int appendFrom) {
    final int head = headEnd;
    final int tail = tailEnd - tailFrom;
    final int appended = encoded.length - appendFrom;
    final byte[] out = new byte[head + tail + appended];
    System.arraycopy(form, 0, out, 0, head);
    System.arraycopy(form, tailFrom, out, head, tail);
    System.arraycopy(encoded, appendFrom, out, head + tail, appended);
    return out;
  }

  private static int indexOf(byte[] array, byte value, int from) {
    for (int i = from; i < array.length; i++) {
      if (array[i] == value) {
        return i;
      }
    }
    return -1;
  }

  private static byte[] slice(byte[] array, int from, int to) {
    final byte[] out = new byte[to - from];
    System.arraycopy(array, from, out, 0, to - from);
    return out;
  }
}
