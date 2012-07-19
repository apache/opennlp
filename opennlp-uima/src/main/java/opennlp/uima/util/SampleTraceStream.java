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

package opennlp.uima.util;

import java.io.IOException;
import java.io.Writer;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * Writes the samples which are processed by this stream to a file.
 * In the case the underlying stream is reseted this stream will
 * detect that, and does not write the samples again to the output writer.
 * @param <T>
 */
public class SampleTraceStream<T> extends FilterObjectStream<T, T> {
  
  private final Writer out;
  
  private boolean wasReseted = false;
  
  public SampleTraceStream(ObjectStream<T> samples, Writer out) {
    super(samples);
    
    this.out = out;
  }
  
  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    super.reset();
    
    wasReseted = true;
  }
  
  public T read() throws IOException {
    
    T sample = samples.read();
    
    if (sample != null && !wasReseted) {
      out.append(sample.toString());
      out.append('\n');
    }
    
    return sample;
  }
}
