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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.CollectionObjectStream;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;

/**
 * Utility class for testing the {@link Tokenizer}.
 */
public class TokenizerTestUtil {

  static TokenizerModel createSimpleMaxentTokenModel() throws IOException {
    List<TokenSample> samples = new ArrayList<>();

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

    TrainingParameters mlParams = new TrainingParameters();
    mlParams.put(TrainingParameters.ITERATIONS_PARAM, 100);
    mlParams.put(TrainingParameters.CUTOFF_PARAM, 0);

    return TokenizerME.train(new CollectionObjectStream<>(samples),
      TokenizerFactory.create(null, "en", null, true, null), mlParams);
  }

  static TokenizerModel createMaxentTokenModel() throws IOException {

    InputStreamFactory trainDataIn = new ResourceAsStreamFactory(
        TokenizerModel.class, "/opennlp/tools/tokenize/token.train");

    ObjectStream<TokenSample> samples = new TokenSampleStream(
        new PlainTextByLineStream(trainDataIn, StandardCharsets.UTF_8));

    TrainingParameters mlParams = new TrainingParameters();
    mlParams.put(TrainingParameters.ITERATIONS_PARAM, 100);
    mlParams.put(TrainingParameters.CUTOFF_PARAM, 0);

    return TokenizerME.train(samples, TokenizerFactory.create(null, "en", null, true, null), mlParams);
  }

}
