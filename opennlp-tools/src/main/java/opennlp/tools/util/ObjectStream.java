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


package opennlp.tools.util;

import java.io.IOException;
import java.io.ObjectStreamException;

/**
 * Reads <code>Object</code>s from a stream.
 * <p>
 * Design Decision:<br>
 * This interface provides a means for iterating over the
 * objects in a stream, it does not implement {@link java.util.Iterator} or
 * {@link Iterable} because:
 * <ul>
 * <li>{@link java.util.Iterator#next()} and
 * {@link java.util.Iterator#hasNext()} are declared as throwing no checked
 * exceptions. Thus the {@link IOException}s thrown by {@link #read()} would
 * have to be wrapped in {@link RuntimeException}s, and the compiler would be
 * unable to force users of this code to catch such exceptions.</li>
 * <li>Implementing {@link Iterable} would mean either silently calling
 * {@link #reset()} to guarantee that all items were always seen on each
 * iteration, or documenting that the Iterable only iterates over the remaining
 * elements of the ObjectStream. In either case, users not reading the
 * documentation carefully might run into unexpected behavior.</li>
 * </ul>
 *
 * @see ObjectStreamException
 */
public interface ObjectStream<T> extends AutoCloseable {

  /**
   * Returns the next object. Calling this method repeatedly until it returns
   * null will return each object from the underlying source exactly once.
   *
   * @return the next object or null to signal that the stream is exhausted
   *
   * @throws IOException if there is an error during reading
   */
  T read() throws IOException;

  /**
   * Repositions the stream at the beginning and the previously seen object sequence
   * will be repeated exactly. This method can be used to re-read
   * the stream if multiple passes over the objects are required.
   *
   * The implementation of this method is optional.
   *
   * @throws IOException if there is an error during reseting the stream
   */
  default void reset() throws IOException, UnsupportedOperationException {
    throw new UnsupportedOperationException("reset is not supported on this stream");
  }

  /**
   * Closes the <code>ObjectStream</code> and releases all allocated
   * resources. After close was called its not allowed to call
   * read or reset.
   *
   * @throws IOException if there is an error during closing the stream
   */
  default void close() throws IOException {}
}
