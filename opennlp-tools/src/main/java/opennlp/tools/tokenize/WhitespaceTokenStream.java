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

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

/**
 * This stream formats a {@link TokenSample}s into whitespace
 * separated token strings.
 */
public class WhitespaceTokenStream extends FilterObjectStream<TokenSample, String> {

  public WhitespaceTokenStream(ObjectStream<TokenSample> tokens) {
    super(tokens);
  }

  public String read() throws IOException {
    TokenSample tokenSample = samples.read();

    if (tokenSample != null) {
      StringBuilder whitespaceSeparatedTokenString = new StringBuilder();

      for (Span token : tokenSample.getTokenSpans()) {
        whitespaceSeparatedTokenString.append(
            token.getCoveredText(tokenSample.getText()));
        whitespaceSeparatedTokenString.append(' ');
      }

      // Shorten string by one to get rid of last space
      if (whitespaceSeparatedTokenString.length() > 0) {
        whitespaceSeparatedTokenString.setLength(
            whitespaceSeparatedTokenString.length() - 1 );
      }

      return whitespaceSeparatedTokenString.toString();
    }

    return null;
  }
}
