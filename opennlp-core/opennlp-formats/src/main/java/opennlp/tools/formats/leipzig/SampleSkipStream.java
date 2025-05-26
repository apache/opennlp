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

package opennlp.tools.formats.leipzig;

import java.io.IOException;

import opennlp.tools.commons.Sample;
import opennlp.tools.util.ObjectStream;

/**
 * A specialization of {@link ObjectStream} that skips a number of samples.
 * @param <T> The template parameter which represents
 *            the {@link Sample} type.
 */
class SampleSkipStream<T> implements ObjectStream<T> {

  private final ObjectStream<T> samples;
  private final int samplesToSkip;

  /**
   * Initializes a {@link SampleSkipStream} with the specified parameters.
   *
   * @param samples       The {@link ObjectStream} to process.
   * @param samplesToSkip The number of samples to skip. Must be greater than {@code 0}.
   * @throws IOException Thrown if IO errors occurred during skip operation.
   */
  SampleSkipStream(ObjectStream<T> samples, int samplesToSkip) throws IOException {
    this.samples = samples;
    this.samplesToSkip = samplesToSkip;

    skipSamples();
  }

  @Override
  public T read() throws IOException {
    return samples.read();
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    this.samples.reset();
    skipSamples();
  }

  private void skipSamples() throws IOException {
    int i = 0;
    while (i < samplesToSkip && (samples.read()) != null) {
      i++;
    }
  }
}
