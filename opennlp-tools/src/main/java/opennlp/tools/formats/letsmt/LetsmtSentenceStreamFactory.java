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

package opennlp.tools.formats.letsmt;

import java.io.File;
import java.io.IOException;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.tokenize.DetokenizationDictionary;
import opennlp.tools.tokenize.Detokenizer;
import opennlp.tools.tokenize.DictionaryDetokenizer;
import opennlp.tools.util.ObjectStream;

public class LetsmtSentenceStreamFactory extends AbstractSampleStreamFactory<SentenceSample> {

  interface Parameters extends BasicFormatParams {
    @ArgumentParser.ParameterDescription(valueName = "dictionary",
        description = "specifies the file with detokenizer dictionary.")
    @ArgumentParser.OptionalParameter
    File getDetokenizer();
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(SentenceSample.class,
        "letsmt", new LetsmtSentenceStreamFactory(
        LetsmtSentenceStreamFactory.Parameters.class));
  }

  protected <P> LetsmtSentenceStreamFactory(Class<P> params) {
    super(params);
  }

  @Override
  public ObjectStream<SentenceSample> create(String[] args) {

    Parameters params = ArgumentParser.parse(args, Parameters.class);

    CmdLineUtil.checkInputFile("Data", params.getData());

    LetsmtDocument letsmtDoc = null;
    try {
      letsmtDoc = LetsmtDocument.parse(params.getData());
    } catch (IOException ex) {
      CmdLineUtil.handleCreateObjectStreamError(ex);
    }

    // TODO:
    // Implement a filter stream to remove splits which are not at an eos char

    ObjectStream<SentenceSample> samples = new LetsmtSentenceStream(letsmtDoc);

    if (params.getDetokenizer() != null) {
      try {
        Detokenizer detokenizer = new DictionaryDetokenizer(
            new DetokenizationDictionary(params.getDetokenizer()));

        samples = new DetokenizeSentenceSampleStream(detokenizer, samples);
      } catch (IOException e) {
        throw new TerminateToolException(-1, "Failed to load detokenizer rules!", e);
      }
    }

    return samples;
  }
}
