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

package opennlp.tools.stopword;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.StringList;

/**
 * An immutable, thread-safe {@link StopwordFilter} backed by an OpenNLP
 * {@link Dictionary}.
 * <p>
 * The backing store supports both 1-gram and n-gram entries. Multi-word
 * entries are queried via {@link #isStopword(String...)}; the
 * {@link #filter(String[])} method performs a greedy left-to-right window
 * scan, preferring the longest registered match at each position.
 * <p>
 * Instances are constructed once and never modified afterwards. Use the
 * {@link Builder} ({@link #builder()}) to assemble a filter from one or
 * more sources (programmatic entries, an input stream, an existing
 * {@link Dictionary}), or the public constructors for the common cases.
 * <p>
 * <strong>Thread-safety:</strong> instances are immutable after
 * construction and may be shared freely across threads without external
 * synchronization. All fields are {@code final}; the only mutation of the
 * backing {@link Dictionary} happens inside the constructor / builder before
 * the instance is published.
 */
@ThreadSafe
public final class DictionaryStopwordFilter implements StopwordFilter {

  private static final String COMMENT_PREFIX = "#";

  private final Dictionary backing;

  /**
   * Loads a stopword list from the given input stream and freezes it into
   * an immutable filter.
   * <p>
   * Format: UTF-8 (or the supplied {@link Charset}), one entry per line.
   * Whitespace-separated tokens on the same line form one multi-word entry.
   * Blank lines and lines starting with {@code #} are skipped.
   *
   * @param in The input stream to read from. Must not be {@code null}.
   * @param cs The {@link Charset} to decode with. Must not be {@code null}.
   * @param caseSensitive Whether matching is case-sensitive.
   * @throws IllegalArgumentException if {@code in} or {@code cs} is
   *     {@code null}.
   * @throws IOException Thrown if an IO error occurs while reading.
   */
  public DictionaryStopwordFilter(final InputStream in, final Charset cs,
                                  final boolean caseSensitive) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("in must not be null");
    }
    if (cs == null) {
      throw new IllegalArgumentException("cs must not be null");
    }
    this.backing = parseStream(in, cs, caseSensitive);
  }

  /**
   * Creates an immutable filter from a defensive copy of {@code source}.
   * Subsequent mutation of {@code source} does not affect this filter.
   *
   * @param source The dictionary whose contents seed the filter. Must not
   *     be {@code null}.
   * @throws IllegalArgumentException if {@code source} is {@code null}.
   */
  public DictionaryStopwordFilter(final Dictionary source) {
    if (source == null) {
      throw new IllegalArgumentException("source must not be null");
    }
    final Dictionary copy = new Dictionary(source.isCaseSensitive());
    for (final StringList entry : source) {
      copy.put(entry);
    }
    this.backing = copy;
  }

  /**
   * @return A new {@link Builder} that assembles a {@link DictionaryStopwordFilter}.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Convenience factory equivalent to
   * {@link #DictionaryStopwordFilter(InputStream, Charset, boolean)} but
   * wrapping any {@link IOException} thrown during reading in an
   * {@link UncheckedIOException}. Useful in contexts where a checked
   * exception is inconvenient (e.g. lambdas, static initializers).
   *
   * @param in The input stream. Must not be {@code null}.
   * @param cs The charset. Must not be {@code null}.
   * @param caseSensitive Whether matching is case-sensitive.
   * @return A new filter loaded from {@code in}.
   * @throws IllegalArgumentException if {@code in} or {@code cs} is
   *     {@code null}.
   * @throws UncheckedIOException if an IO error occurs while reading from
   *     {@code in}.
   */
  public static DictionaryStopwordFilter loadUnchecked(final InputStream in,
                                                       final Charset cs,
                                                       final boolean caseSensitive) {
    try {
      return new DictionaryStopwordFilter(in, cs, caseSensitive);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param token The token to test. May be {@code null}, in which case this
   *     method returns {@code false}.
   * @return {@code true} if {@code token} is registered as a single-token
   *     stopword, {@code false} otherwise.
   */
  @Override
  public boolean isStopword(final CharSequence token) {
    if (token == null) {
      return false;
    }
    return backing.contains(new StringList(token.toString()));
  }

  /**
   * {@inheritDoc}
   *
   * @param tokens The tokens to test as one entry. May be {@code null} or
   *     empty, in which case this method returns {@code false}.
   * @return {@code true} if the sequence is registered as a stopword,
   *     {@code false} otherwise.
   */
  @Override
  public boolean isStopword(final String... tokens) {
    if (tokens == null || tokens.length == 0) {
      return false;
    }
    for (final String t : tokens) {
      if (t == null) {
        return false;
      }
    }
    return backing.contains(new StringList(tokens));
  }

  /**
   * {@inheritDoc}
   *
   * <p>Performs a greedy left-to-right window scan: at each position the
   * longest registered window is tried first. If it matches, those tokens
   * are dropped; otherwise the position advances by one and the current
   * token is kept. {@code null} elements never participate in a window and
   * are kept as-is.
   *
   * @throws IllegalArgumentException if {@code tokens} is {@code null}.
   */
  @Override
  public String[] filter(final String[] tokens) {
    if (tokens == null) {
      throw new IllegalArgumentException("tokens must not be null");
    }
    final int maxWindow = backing.getMaxTokenCount();
    final List<String> kept = new ArrayList<>(tokens.length);
    int i = 0;
    while (i < tokens.length) {
      int matched = 0;
      // Try the longest possible window first, decreasing down to 1.
      for (int w = Math.min(maxWindow, tokens.length - i); w >= 1; w--) {
        if (containsAnyNullInWindow(tokens, i, w)) {
          continue;
        }
        final String[] window = Arrays.copyOfRange(tokens, i, i + w);
        if (backing.contains(new StringList(window))) {
          matched = w;
          break;
        }
      }
      if (matched > 0) {
        i += matched;
      } else {
        kept.add(tokens[i]);
        i++;
      }
    }
    return kept.toArray(new String[0]);
  }

  private static boolean containsAnyNullInWindow(final String[] tokens,
                                                 final int start, final int len) {
    for (int k = 0; k < len; k++) {
      if (tokens[start + k] == null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isCaseSensitive() {
    return backing.isCaseSensitive();
  }

  /**
   * {@inheritDoc}
   *
   * @return An unmodifiable {@link Set} of single-token stopwords. Never
   *     {@code null}.
   * @throws UnsupportedOperationException if a caller attempts to mutate the
   *     returned {@link Set}.
   */
  @Override
  public Set<String> stopwords() {
    return Collections.unmodifiableSet(backing.asStringSet());
  }

  private static Dictionary parseStream(final InputStream in, final Charset cs,
                                        final boolean caseSensitive) throws IOException {
    final Dictionary dict = new Dictionary(caseSensitive);
    try (Reader reader = new InputStreamReader(in, cs);
         BufferedReader lineReader = new BufferedReader(reader)) {
      String line;
      while ((line = lineReader.readLine()) != null) {
        final String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith(COMMENT_PREFIX)) {
          continue;
        }
        final String[] tokens = trimmed.split("\\s+");
        if (tokens.length > 0) {
          dict.put(new StringList(tokens));
        }
      }
    }
    return dict;
  }

  /**
   * Fluent builder for {@link DictionaryStopwordFilter}. Accumulates
   * {@code add} / {@code remove} operations together with a case-sensitivity
   * setting; {@link #build()} produces an immutable filter that reflects the
   * accumulated state.
   * <p>
   * Operations are applied at {@link #build()} time in the order
   * "all adds, then all removes". Within each phase, insertion order is
   * preserved but is not externally observable.
   */
  public static final class Builder {

    private final List<String[]> addEntries = new ArrayList<>();
    private final List<String[]> removeEntries = new ArrayList<>();
    private boolean caseSensitive;

    private Builder() {
      // use DictionaryStopwordFilter.builder()
    }

    /**
     * @param cs Whether the resulting filter performs case-sensitive matching.
     *           Defaults to {@code false}.
     * @return This builder.
     */
    public Builder caseSensitive(final boolean cs) {
      this.caseSensitive = cs;
      return this;
    }

    /**
     * Adds one entry (1-gram or n-gram).
     *
     * @param tokens The tokens forming the entry. Must not be {@code null}
     *               or empty.
     * @return This builder.
     * @throws IllegalArgumentException if {@code tokens} is {@code null} or
     *     empty.
     */
    public Builder add(final String... tokens) {
      if (tokens == null || tokens.length == 0) {
        throw new IllegalArgumentException("tokens must not be null or empty");
      }
      addEntries.add(tokens.clone());
      return this;
    }

    /**
     * Adds a bulk of entries.
     *
     * @param entries The entries to add. Must not be {@code null}.
     * @return This builder.
     * @throws IllegalArgumentException if {@code entries} is {@code null}, or
     *     if any element is {@code null} or empty.
     */
    public Builder addAll(final Collection<String[]> entries) {
      if (entries == null) {
        throw new IllegalArgumentException("entries must not be null");
      }
      for (final String[] entry : entries) {
        add(entry);
      }
      return this;
    }

    /**
     * Schedules removal of one entry (applied after all adds at
     * {@link #build()} time).
     *
     * @param tokens The tokens forming the entry to remove.
     * @return This builder.
     * @throws IllegalArgumentException if {@code tokens} is {@code null} or
     *     empty.
     */
    public Builder remove(final String... tokens) {
      if (tokens == null || tokens.length == 0) {
        throw new IllegalArgumentException("tokens must not be null or empty");
      }
      removeEntries.add(tokens.clone());
      return this;
    }

    /**
     * Schedules a bulk of removals.
     *
     * @param entries The entries to remove. Must not be {@code null}.
     * @return This builder.
     * @throws IllegalArgumentException if {@code entries} is {@code null}, or
     *     if any element is {@code null} or empty.
     */
    public Builder removeAll(final Collection<String[]> entries) {
      if (entries == null) {
        throw new IllegalArgumentException("entries must not be null");
      }
      for (final String[] entry : entries) {
        remove(entry);
      }
      return this;
    }

    /**
     * Reads one-per-line stopword entries from {@code in} (whitespace
     * separates tokens of a multi-word entry; blank and {@code #}-prefixed
     * lines are skipped) and schedules them for addition.
     *
     * @param in The input stream to read from. Must not be {@code null}.
     * @param cs The {@link Charset} to decode with. Must not be {@code null}.
     * @return This builder.
     * @throws IllegalArgumentException if {@code in} or {@code cs} is
     *     {@code null}.
     * @throws IOException If an IO error occurs while reading.
     */
    public Builder load(final InputStream in, final Charset cs) throws IOException {
      if (in == null) {
        throw new IllegalArgumentException("in must not be null");
      }
      if (cs == null) {
        throw new IllegalArgumentException("cs must not be null");
      }
      try (Reader reader = new InputStreamReader(in, cs);
           BufferedReader lineReader = new BufferedReader(reader)) {
        String line;
        while ((line = lineReader.readLine()) != null) {
          final String trimmed = line.trim();
          if (trimmed.isEmpty() || trimmed.startsWith(COMMENT_PREFIX)) {
            continue;
          }
          addEntries.add(trimmed.split("\\s+"));
        }
      }
      return this;
    }

    /**
     * @return A new immutable {@link DictionaryStopwordFilter} reflecting
     *     the accumulated state.
     */
    public DictionaryStopwordFilter build() {
      final Dictionary dict = new Dictionary(caseSensitive);
      for (final String[] entry : addEntries) {
        dict.put(new StringList(entry));
      }
      for (final String[] entry : removeEntries) {
        dict.remove(new StringList(entry));
      }
      return new DictionaryStopwordFilter(dict);
    }
  }
}
