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

package opennlp.tools.formats.conllu;

import java.io.IOException;

import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.StringUtil;

public class ConlluTokenSampleStream extends FilterObjectStream<ConlluSentence, TokenSample> {

  public ConlluTokenSampleStream(ObjectStream<ConlluSentence> samples) {
    super(samples);
  }

  @Override
  public TokenSample read() throws IOException {
    ConlluSentence sentence = samples.read();
    if (sentence != null) {
      if (sentence.getTextComment() != null) {
        StringBuilder text = new StringBuilder(sentence.getTextComment());
        int searchIndex = 0;

        for (ConlluWordLine wordLine : sentence.getWordLines()) {

          // skip over inserted words which are not in the source text
          if (wordLine.getId().contains(".")) {
            continue;
          }

          String token = wordLine.getForm();
          int tokenIndex = text.indexOf(token, searchIndex);

          if (tokenIndex == -1) {
            throw new IOException(String.format("Failed to match token [%s] in sentence [%s] with text [%s]",
                token, sentence.getSentenceIdComment(), text));
          }

          searchIndex = tokenIndex + token.length();
          if (searchIndex < text.length()) {
            if (!StringUtil.isWhitespace(text.charAt(searchIndex))) {
              text.insert(searchIndex,
                  TokenSample.DEFAULT_SEPARATOR_CHARS);
            }
          }
        }
        return TokenSample.parse(text.toString(), TokenSample.DEFAULT_SEPARATOR_CHARS);
      }
      else {
        throw new IOException("Sentence is missing raw text sample!");
      }
    }
    return null;
  }
}
