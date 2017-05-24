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

package opennlp.tools.formats.irishsentencebank;

import java.io.IOException;
import java.util.Iterator;

import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.ObjectStream;

class IrishSentenceBankTokenSampleStream implements ObjectStream<TokenSample>  {

  private final IrishSentenceBankDocument source;

  private Iterator<IrishSentenceBankDocument.IrishSentenceBankSentence> sentenceIt;

  IrishSentenceBankTokenSampleStream(IrishSentenceBankDocument source) {
    this.source = source;
    reset();
  }

  @Override
  public TokenSample read() throws IOException {

    if (sentenceIt.hasNext()) {
      IrishSentenceBankDocument.IrishSentenceBankSentence sentence = sentenceIt.next();
      return sentence.getTokenSample();
    } else {
      return null;
    }
  }

  @Override
  public void reset() {
    sentenceIt = source.getSentences().iterator();
  }
}
