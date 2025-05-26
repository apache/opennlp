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

package opennlp.tools.formats.ad;

import java.nio.charset.Charset;

import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.commons.Internal;
import opennlp.tools.formats.LanguageSampleStreamFactory;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.ObjectStream;

/**
 * <b>Note:</b>
 * Do not use this class, internal use only!
 */
@Internal
public class ADSentenceSampleStreamFactory extends
    LanguageSampleStreamFactory<SentenceSample, ADSentenceSampleStreamFactory.Parameters> {

  public interface Parameters extends BasicFormatParams {
    @ParameterDescription(valueName = "charsetName", description = "encoding for reading and writing text.")
    Charset getEncoding();

    @ParameterDescription(valueName = "language", description = "language which is being processed.")
    String getLang();

    @ParameterDescription(valueName = "includeTitles",
        description = "if true will include sentences marked as headlines.")
    @OptionalParameter(defaultValue = "false")
    Boolean getIncludeTitles();
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(SentenceSample.class, "ad",
        new ADSentenceSampleStreamFactory(Parameters.class));
  }

  protected ADSentenceSampleStreamFactory(Class<Parameters> params) {
    super(params);
  }

  @Override
  public ObjectStream<SentenceSample> create(String[] args) {
    Parameters params = validateBasicFormatParameters(args, Parameters.class);
    language = params.getLang();
    boolean includeTitle = params.getIncludeTitles();
    ObjectStream<String> lineStream = readData(args, Parameters.class);
    return new ADSentenceSampleStream(lineStream, includeTitle);
  }
}
