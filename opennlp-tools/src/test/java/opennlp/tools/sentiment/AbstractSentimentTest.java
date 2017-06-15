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

package opennlp.tools.sentiment;

import java.io.File;
import java.io.IOException;

import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.MockInputStreamFactory;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

public class AbstractSentimentTest {

  protected static final String TRAINING_DATASET = "opennlp/tools/sentiment/sample_train_categ";
  protected static final String MODEL = "opennlp/tools/sentiment/sample_model";
  protected static final String ENCODING = "ISO-8859-1";
  protected static final String LANG = "en";

  private static final SentimentFactory factory = new SentimentFactory();

  protected Sentiment createEmptySentiment() {
    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ITERATIONS_PARAM, 50);
    params.put(TrainingParameters.CUTOFF_PARAM, 1);
    return new SentimentME(LANG, params, factory);
  }

  protected SentimentCrossValidator createCrossValidation() {
    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ITERATIONS_PARAM, 50);
    params.put(TrainingParameters.CUTOFF_PARAM, 1);
    return new SentimentCrossValidator(LANG, params, factory, null);
  }

  protected String[] tokenize(String txt) {
    return factory.getTokenizer().tokenize(txt);
  }

  protected SentimentSampleStream createSampleStream() throws IOException {
    MockInputStreamFactory mockStream = new MockInputStreamFactory(
        new File(TRAINING_DATASET));
    return new SentimentSampleStream(
        new PlainTextByLineStream(mockStream, ENCODING));
  }

  protected Sentiment loadSentiment(File modelFile)
      throws InvalidFormatException, IOException {
    SentimentModel model = new SentimentModel(modelFile);
    return new SentimentME(model);
  }

  protected File saveTempModel() throws IOException {
    Sentiment sentiment = createEmptySentiment();
    SentimentSampleStream sampleStream = createSampleStream();
    SentimentModel model = sentiment.train(sampleStream);
    File temp = File.createTempFile("sample_model", ".tmp");
    CmdLineUtil.writeModel("sentiment analysis", temp, model);
    return temp;
  }
}
