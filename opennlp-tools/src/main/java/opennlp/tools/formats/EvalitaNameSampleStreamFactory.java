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
import opennlp.tools.formats.EvalitaNameSampleStream.LANGUAGE;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class EvalitaNameSampleStreamFactory extends LanguageSampleStreamFactory<NameSample> {

  interface Parameters extends BasicFormatParams {
    @ParameterDescription(valueName = "it")
    String getLang();

    @ParameterDescription(valueName = "per,loc,org,gpe")
    String getTypes();
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(NameSample.class,
        "evalita", new EvalitaNameSampleStreamFactory(Parameters.class));
  }

  protected <P> EvalitaNameSampleStreamFactory(Class<P> params) {
    super(params);
  }

  public ObjectStream<NameSample> create(String[] args) {

    Parameters params = ArgumentParser.parse(args, Parameters.class);

    LANGUAGE lang;
    if ("it".equals(params.getLang())) {
      lang = LANGUAGE.IT;
      language = params.getLang();
    }
    else {
      throw new TerminateToolException(1, "Unsupported language: " + params.getLang());
    }

    int typesToGenerate = 0;

    if (params.getTypes().contains("per")) {
      typesToGenerate = typesToGenerate |
          EvalitaNameSampleStream.GENERATE_PERSON_ENTITIES;
    }
    if (params.getTypes().contains("org")) {
      typesToGenerate = typesToGenerate |
          EvalitaNameSampleStream.GENERATE_ORGANIZATION_ENTITIES;
    }
    if (params.getTypes().contains("loc")) {
      typesToGenerate = typesToGenerate |
          EvalitaNameSampleStream.GENERATE_LOCATION_ENTITIES;
    }
    if (params.getTypes().contains("gpe")) {
      typesToGenerate = typesToGenerate |
          EvalitaNameSampleStream.GENERATE_GPE_ENTITIES;
    }

    try {
      return new EvalitaNameSampleStream(lang,
          CmdLineUtil.createInputStreamFactory(params.getData()), typesToGenerate);
    } catch (IOException e) {
      throw CmdLineUtil.createObjectStreamError(e);
    }
  }
}

