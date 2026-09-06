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
package opennlp.embeddings.eval;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import opennlp.embeddings.index.VectorIndex;

/**
 * An index prepared for queries and the elapsed milliseconds.
 *
 * @param index The completed index.
 * @param millis The elapsed time.
 * @param <T> The index type.
 */
record IndexBuild<T extends VectorIndex>(T index, long millis) {

  static final String SCOPE_KEY = "index.buildScope";
  static final String SCOPE = "construction,insertion,freeze";
  static final String DESCRIPTION = "Build time includes construction, vector insertion and freeze. "
      + "Text embedding and queries are excluded.";

  /**
   * Constructs, populates and freezes an index. Failed builds close resource-backed indexes.
   *
   * @param factory Creates the index.
   * @param populate Adds the vectors.
   * @param nanoTime Supplies monotonic nanoseconds.
   * @param <T> The index type.
   * @return The completed index and elapsed time.
   * @throws RuntimeException If construction, population or freezing fails.
   */
  static <T extends VectorIndex> IndexBuild<T> measure(Supplier<T> factory,
      Consumer<? super T> populate, LongSupplier nanoTime) {
    final long start = nanoTime.getAsLong();
    final T index = factory.get();
    try {
      populate.accept(index);
      index.freeze();
      return new IndexBuild<>(index,
          TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - start));
    } catch (RuntimeException | Error failure) {
      if (index instanceof AutoCloseable closeable) {
        try {
          closeable.close();
        } catch (Throwable closeFailure) {
          failure.addSuppressed(closeFailure);
        }
      }
      throw failure;
    }
  }
}
