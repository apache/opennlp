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

package opennlp.tools.formats.brat;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

public abstract class SegmenterObjectStream<S, T> extends FilterObjectStream<S, T> {

  private Iterator<T> sampleIt = Collections.<T>emptySet().iterator();

  public SegmenterObjectStream(ObjectStream<S> in) {
    super(in);
  }

  protected abstract List<T> read(S sample) throws IOException;

  public final T read() throws IOException {

    if (sampleIt.hasNext()) {
      return sampleIt.next();
    }
    else {
      S inSample = samples.read();

      if (inSample != null) {
        List<T> outSamples = read(inSample);

        if (outSamples != null) {
          sampleIt = outSamples.iterator();
        }

        return read();
      }
    }

    return null;
  }
}
