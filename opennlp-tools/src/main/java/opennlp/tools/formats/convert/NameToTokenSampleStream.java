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

package opennlp.tools.formats.convert;

import java.io.IOException;

import opennlp.tools.namefind.NameSample;
import opennlp.tools.tokenize.Detokenizer;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class NameToTokenSampleStream extends FilterObjectStream<NameSample, TokenSample> {

  private final Detokenizer detokenizer;

  public NameToTokenSampleStream(Detokenizer detokenizer, ObjectStream<NameSample> samples) {
    super(samples);

    this.detokenizer = detokenizer;
  }

  public TokenSample read() throws IOException {
    NameSample nameSample = samples.read();

    TokenSample tokenSample = null;

    if (nameSample != null ) {
      tokenSample = new TokenSample(detokenizer, nameSample.getSentence());
    }

    return tokenSample;
  }

}
