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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import opennlp.tools.commons.Internal;
import opennlp.tools.util.ArgumentChecks;

/**
 * Loads the line-based data files bundled with OpenNLP, such as the Unicode Character Database
 * extracts and the project-authored emoji tables.
 *
 * <p>A load failure surfaces from every accessor call that triggers a load: an
 * {@link IllegalStateException} for a missing resource, an {@link UncheckedIOException} for a
 * resource that cannot be read, and the parser's {@link IllegalArgumentException} for malformed
 * data. A {@link Lazy} holder does not cache a failed load, so a later call tries again.</p>
 */
@Internal
public final class BundledUnicodeData {

  private BundledUnicodeData() {
  }

  /**
   * Parses a bundled data stream.
   *
   * @param <T> The type of the parsed data.
   */
  @FunctionalInterface
  public interface Parser<T> {

    /**
     * Parses {@code in}.
     *
     * @param in The resource stream. Closed by the caller.
     * @return The parsed data.
     * @throws IOException Thrown if reading {@code in} fails.
     */
    T parse(InputStream in) throws IOException;
  }

  /**
   * Receives one line of a data file.
   */
  @FunctionalInterface
  public interface LineConsumer {

    /**
     * Accepts one line.
     *
     * @param line The line, without its terminator.
     * @param lineNumber The one-based number of the line in the file.
     */
    void accept(String line, int lineNumber);
  }

  /**
   * Holds data that is loaded on the first call to {@link #get()}. A load that throws is not
   * cached, so every call until one succeeds runs the loader and throws its exception.
   *
   * @param <T> The type of the data.
   */
  public static final class Lazy<T> {

    private final Supplier<T> loader;

    // Volatile so the loaded value is safely published to every thread that reads it.
    private volatile T value;

    /**
     * Creates a holder that has not loaded yet.
     *
     * @param loader Loads the data. Must not be {@code null} and must not return {@code null}.
     * @throws IllegalArgumentException Thrown if {@code loader} is {@code null}.
     */
    public Lazy(Supplier<T> loader) {
      this.loader = ArgumentChecks.requireNonNullArg(loader, "loader");
    }

    /**
     * Returns the data, loading it on the first successful call.
     *
     * @return The loaded data.
     * @throws IllegalStateException Thrown if the loader returns {@code null}.
     * @throws RuntimeException Thrown as is if the loader fails; the next call loads again.
     */
    public T get() {
      T v = value;
      if (v == null) {
        synchronized (this) {
          v = value;
          if (v == null) {
            v = loader.get();
            if (v == null) {
              throw new IllegalStateException("loader returned null");
            }
            value = v;
          }
        }
      }
      return v;
    }
  }

  /**
   * Opens a resource next to {@code owner} and parses it.
   *
   * @param <T> The type of the parsed data.
   * @param owner The class the resource name is resolved against. Must not be {@code null}.
   * @param resource The resource name. Must not be {@code null}.
   * @param description The kind of data, used in error messages, for example
   *     {@code "Word_Break"}. Must not be {@code null}.
   * @param parser Parses the opened stream. Must not be {@code null}.
   * @return The parsed data.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}, or rethrown from
   *     {@code parser} for malformed data.
   * @throws IllegalStateException Thrown if the resource is missing.
   * @throws UncheckedIOException Thrown if the resource cannot be read.
   */
  public static <T> T load(Class<?> owner, String resource, String description,
                           Parser<T> parser) {
    ArgumentChecks.requireNonNullArg(owner, "owner");
    ArgumentChecks.requireNonNullArg(resource, "resource");
    ArgumentChecks.requireNonNullArg(description, "description");
    ArgumentChecks.requireNonNullArg(parser, "parser");
    try (InputStream in = owner.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Missing " + description + " data resource: " + resource);
      }
      return parser.parse(in);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Unable to read " + description + " data resource " + resource, e);
    }
  }

  /**
   * Reads every line of {@code in} and closes it.
   *
   * @param in The stream to read. Must not be {@code null}.
   * @param charset The encoding of {@code in}. Must not be {@code null}.
   * @param consumer Receives each line with its number. Must not be {@code null}.
   * @throws IOException Thrown if reading {@code in} fails.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}.
   */
  public static void forEachLine(InputStream in, Charset charset, LineConsumer consumer)
      throws IOException {
    ArgumentChecks.requireNonNullArg(in, "in");
    ArgumentChecks.requireNonNullArg(charset, "charset");
    ArgumentChecks.requireNonNullArg(consumer, "consumer");
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, charset))) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        consumer.accept(line, lineNumber);
      }
    }
  }

  /**
   * Reads the content lines of a UTF-8 data file in the Unicode Character Database layout and
   * closes the stream. Each line is passed without its {@code #} comment and surrounding
   * whitespace; lines that are empty after that are skipped but still counted.
   *
   * @param in The stream to read. Must not be {@code null}.
   * @param consumer Receives each non-empty content line with its number. Must not be
   *     {@code null}.
   * @throws IOException Thrown if reading {@code in} fails.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}.
   */
  public static void forEachContentLine(InputStream in, LineConsumer consumer)
      throws IOException {
    ArgumentChecks.requireNonNullArg(consumer, "consumer");
    forEachLine(in, StandardCharsets.UTF_8, (line, lineNumber) -> {
      final String content = HexCodePoints.stripComment(line).strip();
      if (!content.isEmpty()) {
        consumer.accept(content, lineNumber);
      }
    });
  }
}
