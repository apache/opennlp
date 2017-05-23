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

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;

public class ConlluSentenceSampleStreamFactory extends AbstractSampleStreamFactory<SentenceSample> {

  interface Parameters extends BasicFormatParams {
    @ArgumentParser.ParameterDescription(valueName = "sentencesPerSample",
        description = "number of sentences per sample")
    String getSentencesPerSample();
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(SentenceSample.class,
        ConlluPOSSampleStreamFactory.CONLLU_FORMAT,
        new ConlluSentenceSampleStreamFactory(ConlluSentenceSampleStreamFactory.Parameters.class));
  }

  protected <P> ConlluSentenceSampleStreamFactory(Class<P> params) {
    super(params);
  }

  @Override
  public ObjectStream<SentenceSample> create(String[] args) {
    Parameters params = ArgumentParser.parse(args, Parameters.class);

    InputStreamFactory inFactory =
        CmdLineUtil.createInputStreamFactory(params.getData());

    try {
      return new ConlluSentenceSampleStream(new ConlluStream(inFactory),
          Integer.parseInt(params.getSentencesPerSample()));
    } catch (IOException e) {
      // That will throw an exception
      CmdLineUtil.handleCreateObjectStreamError(e);
    }
    return null;
  }
}
