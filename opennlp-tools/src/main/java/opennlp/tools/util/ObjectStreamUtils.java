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
import java.util.Collection;
import java.util.Iterator;

public class ObjectStreamUtils {

  /**
   * Creates an {@link ObjectStream} form an array of {@link T}.
   *
   * @param array The elements to feed into the new {@link ObjectStream}.
   * @param <T> The generic type of the elements in the {@code array}.
   *
   * @return The {@link ObjectStream} over the array elements.
   */
  @SafeVarargs
  public static <T> ObjectStream<T> createObjectStream(final T... array) {

    return new ObjectStream<>() {

      private int index = 0;

      @Override
      public T read() {
        if (index < array.length)
          return array[index++];
        else
          return null;
      }

      @Override
      public void reset() {
        index = 0;
      }

      @Override
      public void close() {
      }
    };
  }

  /**
   * Creates an {@link ObjectStream} form a {@link Collection<T>}.
   *
   * @param collection The elements to feed into the new {@link ObjectStream}.
   * @param <T> The generic type of the elements in the {@code collection}.
   *
   * @return The {@link ObjectStream} over the collection elements
   */
  public static <T> ObjectStream<T> createObjectStream(final Collection<T> collection) {

    return new ObjectStream<>() {

      private Iterator<T> iterator = collection.iterator();

      @Override
      public T read() {
        if (iterator.hasNext())
          return iterator.next();
        else
          return null;
      }

      @Override
      public void reset() {
        iterator = collection.iterator();
      }

      @Override
      public void close() {
      }
    };
  }

  /**
   * Creates a single concatenated {@link ObjectStream} from multiple individual
   * {@link ObjectStream streams} with the same type {@link T}.
   *
   * @param streams The collection of streams to feed into the concatenated {@link ObjectStream}.
   *                Every element of the collection must not be {@code null}.
   * @param <T> The generic type of the elements in the {@code collection}.
   *
   * @return The concatenated {@link ObjectStream} aggregating all elements in {@code streams}.
   */
  public static <T> ObjectStream<T> concatenateObjectStream(final Collection<ObjectStream<T>> streams) {

    // We may want to skip null streams instead of throwing a 
    for (ObjectStream<T> stream : streams) {
      if (stream == null) {
        throw new NullPointerException("stream cannot be null");
      }
    }

    return new ObjectStream<>() {

      private Iterator<ObjectStream<T>> iterator = streams.iterator();
      private ObjectStream<T> currentStream = iterator.next();

      @Override
      public T read() throws IOException {
        T object = null;

        while (currentStream != null && object == null) {
          object = currentStream.read();
          if (object == null) {
            currentStream = (iterator.hasNext()) ? iterator.next() : null;
          }
        }
        return object;
      }

      @Override
      public void reset() throws IOException, UnsupportedOperationException {
        for (ObjectStream<T> stream : streams) {
          stream.reset();
        }
        iterator = streams.iterator();
      }

      @Override
      public void close() throws IOException {
        for (ObjectStream<T> stream : streams) {
          stream.close();
        }
      }

    };

  }

  /**
   * Creates a single concatenated {@link ObjectStream} from multiple individual
   * {@link ObjectStream streams} with the same type.
   *
   * @param streams One or more stream to feed into the concatenated {@link ObjectStream}.
   *                Every element of the collection must not be {@code null}.
   * @param <T> The generic type of the elements in the {@code streams}.
   *           
   * @return The concatenated {@link ObjectStream} aggregating all elements in {@code streams}.
   */
  @SafeVarargs
  public static <T> ObjectStream<T> concatenateObjectStream(final ObjectStream<T>... streams) {

    for (ObjectStream<T> stream : streams) {
      if (stream == null) {
        throw new NullPointerException("stream cannot be null");
      }
    }

    return new ObjectStream<>() {

      private int streamIndex = 0;

      @Override
      public T read() throws IOException {

        T object = null;

        while (streamIndex < streams.length && object == null) {
          object = streams[streamIndex].read();

          if (object == null)
            streamIndex++;
        }

        return object;
      }

      @Override
      public void reset() throws IOException, UnsupportedOperationException {
        streamIndex = 0;

        for (ObjectStream<T> stream : streams) {
          stream.reset();
        }
      }

      @Override
      public void close() throws IOException {

        for (ObjectStream<T> stream : streams) {
          stream.close();
        }
      }
    };
  }
}
