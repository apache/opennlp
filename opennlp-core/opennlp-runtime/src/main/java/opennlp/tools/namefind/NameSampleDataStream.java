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

package opennlp.tools.namefind;

import java.io.IOException;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * The {@link NameSampleDataStream} class converts tagged tokens
 * provided by a DataStream to {@link NameSample} objects.
 * <p>
 * It uses text that is one-sentence per line and tokenized
 * with names identified by: <br/>
 * &lt;{@code START}&gt; and &lt;{@code END}&gt;tags.
 */
public class NameSampleDataStream extends FilterObjectStream<String, NameSample> {

  /**
   * Initializes a {@link NameSampleDataStream} with given {@code psi} samples.
   *
   * @param in The {@link ObjectStream stream} of data samples.
   */
  public NameSampleDataStream(ObjectStream<String> in) {
    super(in);
  }

  @Override
  public NameSample read() throws IOException {
    String token = samples.read();

    boolean isClearAdaptiveData = false;

    // An empty line indicates the start of a new article
    // for which the adaptive data in the feature generators
    // must be cleared
    while (token != null && token.trim().isEmpty()) {
      isClearAdaptiveData = true;
      token = samples.read();
    }

    if (token != null) {
      return NameSample.parse(token, isClearAdaptiveData);
    }
    else {
      return null;
    }
  }
}
