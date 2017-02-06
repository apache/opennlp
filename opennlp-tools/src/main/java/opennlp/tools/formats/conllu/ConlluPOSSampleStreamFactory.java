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
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.postag.POSSample;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class ConlluPOSSampleStreamFactory extends AbstractSampleStreamFactory<POSSample> {

  public static final String CONLLU_FORMAT = "conllu";

  interface Parameters extends BasicFormatParams {
    @ArgumentParser.ParameterDescription(valueName = "tagset",
        description = "u|x u for unified tags and x for language-specific part-of-speech tags")
    @ArgumentParser.OptionalParameter(defaultValue = "u")
    String getTagset();
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(POSSample.class,
        CONLLU_FORMAT, new ConlluPOSSampleStreamFactory(Parameters.class));
  }

  protected <P> ConlluPOSSampleStreamFactory(Class<P> params) {
    super(params);
  }

  public ObjectStream<POSSample> create(String[] args) {
    Parameters params = ArgumentParser.parse(args, Parameters.class);

    ConlluTagset tagset;

    switch (params.getTagset()) {
      case "u":
        tagset = ConlluTagset.U;
        break;
      case  "x":
        tagset = ConlluTagset.X;
        break;
      default:
        throw new TerminateToolException(-1, "Unkown tagset parameter: " + params.getTagset());
    }

    InputStreamFactory inFactory =
        CmdLineUtil.createInputStreamFactory(params.getData());

    try {
      return new ConlluPOSSampleStream(new ConlluStream(inFactory), tagset);
    } catch (IOException e) {
      // That will throw an exception
      CmdLineUtil.handleCreateObjectStreamError(e);
    }
    return null;
  }
}
