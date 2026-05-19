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

import java.io.IOException;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * A {@link FilterObjectStream} which removes stopwords from each
 * {@code String[]} sample produced by an underlying
 * {@link ObjectStream ObjectStream&lt;String[]&gt;}.
 * <p>
 * Stopword membership is decided by the supplied {@link StopwordFilter};
 * filtering is delegated to {@link StopwordFilter#filter(String[])} so the
 * relative order of surviving tokens within a sample is preserved.
 * <p>
 * {@link #reset()} and {@link #close()} are inherited from
 * {@link FilterObjectStream} and simply forward to the wrapped stream.
 */
public final class StopwordFilterStream extends FilterObjectStream<String[], String[]> {

  private final StopwordFilter filter;

  /**
   * Initializes a {@link StopwordFilterStream}.
   *
   * @param samples The {@link ObjectStream} of token arrays to filter.
   *                Must not be {@code null}.
   * @param filter  The {@link StopwordFilter} used to drop stopwords.
   *                Must not be {@code null}.
   * @throws IllegalArgumentException if {@code samples} or {@code filter} is
   *                                  {@code null}.
   */
  public StopwordFilterStream(final ObjectStream<String[]> samples,
                              final StopwordFilter filter) {
    super(requireNonNullArg(samples, "samples"));
    requireNonNullArg(filter, "filter");
    this.filter = filter;
  }

  /**
   * Reads the next sample from the wrapped stream and returns it with
   * stopwords removed. Returns {@code null} once the underlying stream is
   * exhausted.
   *
   * @return The filtered sample or {@code null} at the end of the stream.
   * @throws IOException If the underlying stream throws an I/O error.
   */
  @Override
  public String[] read() throws IOException {
    final String[] in = samples.read();
    return in == null ? null : filter.filter(in);
  }

  private static <T> T requireNonNullArg(final T value, final String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
    return value;
  }
}
