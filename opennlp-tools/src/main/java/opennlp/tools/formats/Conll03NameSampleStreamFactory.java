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
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.formats.Conll03NameSampleStream.LANGUAGE;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;

public class Conll03NameSampleStreamFactory extends LanguageSampleStreamFactory<NameSample> {

  interface Parameters extends BasicFormatParams {
    @ParameterDescription(valueName = "en|de")
    String getLang();

    @ParameterDescription(valueName = "per,loc,org,misc")
    String getTypes();
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(NameSample.class,
        "conll03", new Conll03NameSampleStreamFactory(Parameters.class));
  }

  protected <P> Conll03NameSampleStreamFactory(Class<P> params) {
    super(params);
  }

  public ObjectStream<NameSample> create(String[] args) {

    Parameters params = ArgumentParser.parse(args, Parameters.class);

    // TODO: support the other languages with this CoNLL.
    LANGUAGE lang;
    if ("en".equals(params.getLang())) {
      lang = LANGUAGE.EN;
      language = params.getLang();
    }
    else if ("de".equals(params.getLang())) {
      lang = LANGUAGE.DE;
      language = params.getLang();
    }
    else {
      throw new TerminateToolException(1, "Unsupported language: " + params.getLang());
    }

    int typesToGenerate = 0;

    if (params.getTypes().contains("per")) {
      typesToGenerate = typesToGenerate |
          Conll02NameSampleStream.GENERATE_PERSON_ENTITIES;
    }
    if (params.getTypes().contains("org")) {
      typesToGenerate = typesToGenerate |
          Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES;
    }
    if (params.getTypes().contains("loc")) {
      typesToGenerate = typesToGenerate |
          Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES;
    }
    if (params.getTypes().contains("misc")) {
      typesToGenerate = typesToGenerate |
          Conll02NameSampleStream.GENERATE_MISC_ENTITIES;
    }

    try {
      return new Conll03NameSampleStream(lang,
          CmdLineUtil.createInputStreamFactory(params.getData()), typesToGenerate);
    } catch (IOException e) {
      throw CmdLineUtil.createObjectStreamError(e);
    }
  }
}
