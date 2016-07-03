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

import edu.usc.ir.sentiment.analysis.cmdline.CLI;
import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.sentiment.SentimentSample;
import opennlp.tools.sentiment.SentimentSampleStream;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * Class for creating a sample stream factory for sentiment analysis.
 */
public class SentimentSampleStreamFactory
    extends AbstractSampleStreamFactory<SentimentSample> {

  /**
   * The constructor of the class; initialises the factory
   *
   * @param params
   *          any given parameters
   */
  protected <P> SentimentSampleStreamFactory(Class<P> params) {
    super(params);
  }

  /**
   * Creates a sentiment sample stream factory
   *
   * @param args
   *          the necessary arguments
   * @return SentimentSample stream (factory)
   */
  @Override
  public ObjectStream<SentimentSample> create(String[] args) {
    BasicFormatParams params = ArgumentParser.parse(args,
        BasicFormatParams.class);

    CmdLineUtil.checkInputFile("Data", params.getData());
    InputStreamFactory sampleDataIn = CmdLineUtil
        .createInputStreamFactory(params.getData());
    ObjectStream<String> lineStream = null;
    try {
      lineStream = new PlainTextByLineStream(sampleDataIn,
          params.getEncoding());
    } catch (IOException ex) {
      CmdLineUtil.handleCreateObjectStreamError(ex);
    }

    return new SentimentSampleStream(lineStream);
  }

  /**
   * Registers a SentimentSample stream factory
   */
  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(SentimentSample.class,
        CLI.DEFAULT_FORMAT,
        new SentimentSampleStreamFactory(BasicFormatParams.class));
  }

}
