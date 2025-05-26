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
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;

/**
 * A Factory to create a Arvores Deitadas NameSampleDataStream from the command line
 * utility.
 * <p>
 * <b>Note:</b>
 * Do not use this class, internal use only!
 */
@Internal
public class ADNameSampleStreamFactory extends
        LanguageSampleStreamFactory<NameSample, ADNameSampleStreamFactory.Parameters> {

  public interface Parameters extends BasicFormatParams {
    //all have to be repeated, because encoding is not optional,
    //according to the check if (encoding == null) { below (now removed)
    @ParameterDescription(valueName = "charsetName",
        description = "encoding for reading and writing text, if absent the system default is used.")
    Charset getEncoding();

    @ParameterDescription(valueName = "split",
        description = "if true all hyphenated tokens will be separated (default true)")
    @OptionalParameter(defaultValue = "true")
    Boolean getSplitHyphenatedTokens();

    @ParameterDescription(valueName = "language", description = "language which is being processed.")
    String getLang();
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(NameSample.class,
        "ad", new ADNameSampleStreamFactory(Parameters.class));
  }

  protected ADNameSampleStreamFactory(Class<Parameters> params) {
    super(params);
  }

  @Override
  public ObjectStream<NameSample> create(String[] args) {
    Parameters params = validateBasicFormatParameters(args, Parameters.class);
    language = params.getLang();
    ObjectStream<String> lineStream = readData(args, Parameters.class);
    return new ADNameSampleStream(lineStream, params.getSplitHyphenatedTokens());
  }
}
