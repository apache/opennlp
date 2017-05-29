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

package opennlp.tools.formats;

import java.io.IOException;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.params.SentenceDetectorFormatParams;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.sentdetect.SentenceSampleStream;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;


/**
 * Factory producing OpenNLP {@link SentenceSampleStream}s.
 */
public class SentenceSampleStreamFactory extends AbstractSampleStreamFactory<SentenceSample> {

  interface Parameters extends SentenceDetectorFormatParams {

  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(SentenceSample.class,
        StreamFactoryRegistry.DEFAULT_FORMAT, new SentenceSampleStreamFactory(Parameters.class));
  }

  protected <P> SentenceSampleStreamFactory(Class<P> params) {
    super(params);
  }

  public ObjectStream<SentenceSample> create(String[] args) {
    Parameters params = ArgumentParser.parse(args, Parameters.class);

    CmdLineUtil.checkInputFile("Data", params.getData());
    InputStreamFactory sampleDataIn = CmdLineUtil.createInputStreamFactory(params.getData());

    ObjectStream<String> lineStream = null;
    try {
      lineStream = new PlainTextByLineStream(sampleDataIn, params.getEncoding());
    } catch (IOException ex) {
      CmdLineUtil.handleCreateObjectStreamError(ex);
    }

    char[] eos = extractEOS(params);

    Character defaultEOS = extractDefaultEOS(params);


    return new SentenceSampleStream(lineStream, eos, defaultEOS);
  }

  public static char[] extractEOS(SentenceDetectorFormatParams params) {
    char[] eos = null;
    if (params.getEosChars() != null) {
      String eosString = SentenceSampleStream.replaceNewLineEscapeTags(
          params.getEosChars());
      eos = eosString.toCharArray();
    }
    return eos;
  }

  public static Character extractDefaultEOS(SentenceDetectorFormatParams params) {
    Character defaultEOS = null;
    if (params.getDefaultEosChars() != null) {
      String eosString = SentenceSampleStream.replaceNewLineEscapeTags(
          params.getEosChars());
      if (eosString != null && eosString.length() > 0) {
        defaultEOS = eosString.toCharArray()[0];
      }

    }
    return defaultEOS;
  }
}
