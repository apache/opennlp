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

/**
 * Abstract base class for filtering {@link ObjectStream}s.
 * <p>
 * Filtering streams take an existing stream and convert
 * its output to something else.
 *
 * @param <S> the type of the source/input stream
 * @param <T> the type of this stream
 */
public abstract class FilterObjectStream<S, T> implements ObjectStream<T> {

  protected final ObjectStream<S> samples;

  protected FilterObjectStream(ObjectStream<S> samples) {
    if (samples == null) {
      throw new IllegalArgumentException("samples must not be null!");
    }

    this.samples = samples;
  }

  public void reset() throws IOException, UnsupportedOperationException {
    samples.reset();
  }

  public void close() throws IOException {
    samples.close();
  }
}
