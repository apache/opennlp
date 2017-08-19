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

package opennlp.tools.formats.leipzig;

import java.io.File;
import java.io.IOException;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.EncodingParameter;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.langdetect.LanguageSample;
import opennlp.tools.util.ObjectStream;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class LeipzigLanguageSampleStreamFactory
    extends AbstractSampleStreamFactory<LanguageSample> {

  interface Parameters extends EncodingParameter {
    @ParameterDescription(valueName = "sentencesDir",
        description = "dir with Leipig sentences to be used")
    File getSentencesDir();

    @ParameterDescription(valueName = "sentencesPerSample",
        description = "number of sentences per sample")
    String getSentencesPerSample();

    @ParameterDescription(valueName = "samplesPerLanguage",
        description = "number of samples per language")
    String getSamplesPerLanguage();

    @ParameterDescription(valueName = "samplesToSkip",
        description = "number of samples to skip before returning")
    @OptionalParameter(defaultValue = "0")
    String getSamplesToSkip();
  }

  protected <P> LeipzigLanguageSampleStreamFactory(Class<P> params) {
    super(params);
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(LanguageSample.class,
        "leipzig", new LeipzigLanguageSampleStreamFactory(Parameters.class));
  }

  public ObjectStream<LanguageSample> create(String[] args) {

    Parameters params = ArgumentParser.parse(args, Parameters.class);
    File sentencesFileDir = params.getSentencesDir();

    try {
      return new SampleSkipStream(new SampleShuffleStream(
          new LeipzigLanguageSampleStream(sentencesFileDir,
          Integer.parseInt(params.getSentencesPerSample()),
          Integer.parseInt(params.getSamplesPerLanguage()) + Integer.parseInt(params.getSamplesToSkip()))),
          Integer.parseInt(params.getSamplesToSkip()));
    } catch (IOException e) {
      throw new TerminateToolException(-1, "IO error while opening sample data.", e);
    }
  }
}
