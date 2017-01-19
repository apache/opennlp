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
   * Creates an {@link ObjectStream} form an array.
   *
   * @param <T>
   * @param array
   *
   * @return the object stream over the array elements
   */
  @SafeVarargs
  public static <T> ObjectStream<T> createObjectStream(final T... array) {

    return new ObjectStream<T>() {

      private int index = 0;

      public T read() {
        if (index < array.length)
          return array[index++];
        else
          return null;
      }

      public void reset() {
        index = 0;
      }

      public void close() {
      }
    };
  }

  /**
   * Creates an {@link ObjectStream} form a collection.
   *
   * @param <T>
   * @param collection
   *
   * @return the object stream over the collection elements
   */
  public static <T> ObjectStream<T> createObjectStream(final Collection<T> collection) {

    return new ObjectStream<T>() {

      private Iterator<T> iterator = collection.iterator();

      public T read() {
        if (iterator.hasNext())
          return iterator.next();
        else
          return null;
      }

      public void reset() {
        iterator = collection.iterator();
      }

      public void close() {
      }
    };
  }

  /**
   * Creates a single concatenated ObjectStream from multiple individual
   * ObjectStreams with the same type.
   *
   * @param streams
   * @return
   */
  @SafeVarargs
  public static <T> ObjectStream<T> createObjectStream(final ObjectStream<T>... streams) {

    for (ObjectStream<T> stream : streams) {
      if (stream == null) {
        throw new NullPointerException("stream cannot be null");
      }
    }

    return new ObjectStream<T>() {

      private int streamIndex = 0;

      public T read() throws IOException {

        T object = null;

        while (streamIndex < streams.length && object == null) {
          object = streams[streamIndex].read();

          if (object == null)
            streamIndex++;
        }

        return object;
      }

      public void reset() throws IOException, UnsupportedOperationException {
        streamIndex = 0;

        for (ObjectStream<T> stream : streams) {
          stream.reset();
        }
      }

      public void close() throws IOException {

        for (ObjectStream<T> stream : streams) {
          stream.close();
        }
      }
    };
  }
}
