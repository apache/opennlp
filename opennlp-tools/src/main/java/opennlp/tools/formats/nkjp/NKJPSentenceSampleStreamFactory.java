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

package opennlp.tools.formats.nkjp;

import java.io.File;
import java.io.IOException;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.ObjectStream;

public class NKJPSentenceSampleStreamFactory extends AbstractSampleStreamFactory<SentenceSample> {

  interface Parameters extends BasicFormatParams {
    @ArgumentParser.ParameterDescription(valueName = "text",
        description = "file containing NKJP text")
    File getTextFile();
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(SentenceSample.class,
        "nkjp", new NKJPSentenceSampleStreamFactory(
        NKJPSentenceSampleStreamFactory.Parameters.class));
  }

  protected <P> NKJPSentenceSampleStreamFactory(Class<P> params) {
    super(params);
  }

  @Override
  public ObjectStream<SentenceSample> create(String[] args) {

    Parameters params = ArgumentParser.parse(args, Parameters.class);

    CmdLineUtil.checkInputFile("Data", params.getData());

    CmdLineUtil.checkInputFile("Text", params.getTextFile());

    NKJPSegmentationDocument segDoc = null;
    NKJPTextDocument textDoc = null;
    try {
      segDoc = NKJPSegmentationDocument.parse(params.getData());
      textDoc = NKJPTextDocument.parse(params.getTextFile());
    } catch (IOException ex) {
      CmdLineUtil.handleCreateObjectStreamError(ex);
    }

    return new NKJPSentenceSampleStream(segDoc, textDoc);
  }
}
