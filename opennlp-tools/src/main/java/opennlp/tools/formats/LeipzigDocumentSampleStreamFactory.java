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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.EncodingParameter;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class LeipzigDocumentSampleStreamFactory
    extends AbstractSampleStreamFactory<DocumentSample> {

  protected <P> LeipzigDocumentSampleStreamFactory(Class<P> params) {
    super(params);
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(DocumentSample.class,
        "leipzig", new LeipzigDocumentSampleStreamFactory(Parameters.class));
  }

  public ObjectStream<DocumentSample> create(String[] args) {

    Parameters params = ArgumentParser.parse(args, Parameters.class);
    File sentencesFileDir = params.getSentencesDir();

    File sentencesFiles[] = sentencesFileDir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.contains("sentences") && name.endsWith(".txt");
      }
    });

    @SuppressWarnings("unchecked")
    ObjectStream<DocumentSample> sampleStreams[] =
        new ObjectStream[sentencesFiles.length];

    for (int i = 0; i < sentencesFiles.length; i++) {
      try {
        sampleStreams[i] = new LeipzigDoccatSampleStream(
            sentencesFiles[i].getName().substring(0, 3), 20,
            CmdLineUtil.createInputStreamFactory(sentencesFiles[i]));
      } catch (IOException e) {
        throw new TerminateToolException(-1, "IO error while opening sample data: " + e.getMessage(), e);
      }
    }

    return ObjectStreamUtils.createObjectStream(sampleStreams);
  }

  interface Parameters extends EncodingParameter {
    @ParameterDescription(valueName = "sentencesDir",
        description = "dir with Leipig sentences to be used")
    File getSentencesDir();
  }
}
