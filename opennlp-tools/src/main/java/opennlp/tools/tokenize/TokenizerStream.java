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


package opennlp.tools.tokenize;

import java.io.IOException;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

/**
 * The {@link TokenizerStream} uses a tokenizer to tokenize the
 * input string and output {@link TokenSample}s.
 */
public class TokenizerStream implements ObjectStream<TokenSample> {

  private Tokenizer tokenizer;
  private ObjectStream<String> input;

  public TokenizerStream(Tokenizer tokenizer, ObjectStream<String> input) {
    this.tokenizer = tokenizer;
    this.input = input;
  }

  public TokenSample read() throws IOException {
    String inputString = input.read();

    if (inputString != null) {
      Span tokens[] = tokenizer.tokenizePos(inputString);

      return new TokenSample(inputString, tokens);
    }

    return null;
  }

  public void close() throws IOException {
    input.close();
  }

  public void reset() throws IOException,
      UnsupportedOperationException {
    input.reset();
  }
}
