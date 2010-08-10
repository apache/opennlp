/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.CollectionObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

/**
 * Utility class for testing the {@link Tokenizer}.
 */
public class TokenizerTestUtil {

  static TokenizerModel createSimpleMaxentTokenModel() throws IOException {
    List<TokenSample> samples = new ArrayList<TokenSample>();

    samples.add(new TokenSample("year", new Span[]{new Span(0, 4)}));
    samples.add(new TokenSample("year,", new Span[]{
        new Span(0, 4),
        new Span(4, 5)}));
    samples.add(new TokenSample("it,", new Span[]{
        new Span(0, 2),
        new Span(2, 3)}));
    samples.add(new TokenSample("it", new Span[]{
        new Span(0, 2)}));
    samples.add(new TokenSample("yes", new Span[]{
        new Span(0, 3)}));
    samples.add(new TokenSample("yes,", new Span[]{
        new Span(0, 3),
        new Span(3, 4)}));

    return TokenizerME.train("en", new CollectionObjectStream<TokenSample>(samples), true,
                             5, 100);
  }

  static TokenizerModel createMaxentTokenModel() throws IOException {
    
    InputStream trainDataIn = TokenizerTestUtil.class.getResourceAsStream(
        "/opennlp/tools/tokenize/token.train");
    
    ObjectStream<TokenSample> samples = new TokenSampleStream(
        new PlainTextByLineStream(new InputStreamReader(trainDataIn, "UTF-8")));
    
    return TokenizerME.train("en", samples, true, 5, 100);
  }
  
}
