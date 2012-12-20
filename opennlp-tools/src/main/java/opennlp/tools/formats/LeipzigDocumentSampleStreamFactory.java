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
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.cmdline.params.LanguageParams;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.util.ObjectStream;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class LeipzigDocumentSampleStreamFactory extends LanguageSampleStreamFactory<DocumentSample> {

  interface Parameters extends BasicFormatParams, LanguageParams {
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(DocumentSample.class,
        "leipzig", new LeipzigDocumentSampleStreamFactory(Parameters.class));
  }

  protected <P> LeipzigDocumentSampleStreamFactory(Class<P> params) {
    super(params);
  }

  public ObjectStream<DocumentSample> create(String[] args) {
    
    Parameters params = ArgumentParser.parse(args, Parameters.class);
    language = params.getLang();

    try {
      return new LeipzigDoccatSampleStream(params.getLang(), 20,
          CmdLineUtil.openInFile(params.getData()));
    } catch (IOException e) {
      throw new TerminateToolException(-1, "IO error while opening sample data: " + e.getMessage(), e);
    }
  }
}
