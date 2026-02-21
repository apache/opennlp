/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.formats;

import java.io.IOException;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.sentiment.SentimentSample;
import opennlp.tools.sentiment.SentimentSampleStream;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * Factory for creating a sample stream factory for sentiment analysis.
 *
 * @see SentimentSample
 */
public class SentimentSampleStreamFactory<P> extends AbstractSampleStreamFactory<SentimentSample, P> {

  /**
   * Instantiates a {@link SentimentSampleStreamFactory} object.
   *
   * @param params
   *          any given parameters
   */
  protected SentimentSampleStreamFactory(Class<P> params) {
    super(params);
  }

  /**
   * Creates a sentiment sample stream.
   *
   * @param args
   *          the necessary arguments
   * @return A {@link SentimentSample} stream.
   */
  @Override
  public ObjectStream<SentimentSample> create(String[] args) {
    BasicFormatParams params = ArgumentParser.parse(args, BasicFormatParams.class);

    FormatUtil.checkInputFile("Data", params.getData());
    ObjectStream<String> lineStream;
    try {
      InputStreamFactory sampleDataIn = FormatUtil.createInputStreamFactory(params.getData());
      lineStream = new PlainTextByLineStream(sampleDataIn, params.getEncoding());
    } catch (IOException ex) {
      throw new TerminateToolException(-1,
              "IO Error while creating an Input Stream: " + ex.getMessage(), ex);
    }
    return new SentimentSampleStream(lineStream);
  }

  /**
   * Registers a SentimentSample stream factory
   */
  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(SentimentSample.class, StreamFactoryRegistry.DEFAULT_FORMAT,
        new SentimentSampleStreamFactory<>(BasicFormatParams.class));
  }

}
