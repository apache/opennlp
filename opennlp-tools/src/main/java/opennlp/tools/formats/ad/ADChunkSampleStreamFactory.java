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

import java.io.File;
import java.nio.charset.Charset;

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.ObjectStreamFactory;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.util.ObjectStream;

/**
 * A Factory to create a Arvores Deitadas ChunkStream from the command line
 * utility.
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class ADChunkSampleStreamFactory implements
    ObjectStreamFactory<ChunkSample> {

  interface Parameters {
    @ParameterDescription(valueName = "encoding")
    String getEncoding();

    @ParameterDescription(valueName = "sampleData")
    String getData();
    
    @ParameterDescription(valueName = "start", description = "index of first sentence")
    @OptionalParameter
    Integer getStart();
    
    @ParameterDescription(valueName = "end", description = "index of last sentence")
    @OptionalParameter
    Integer getEnd();
  }

  public String getUsage() {
    return ArgumentParser.createUsage(Parameters.class);
  }

  public boolean validateArguments(String[] args) {
    return ArgumentParser.validateArguments(args, Parameters.class);
  }

  public ObjectStream<ChunkSample> create(String[] args) {

    Parameters params = ArgumentParser.parse(args, Parameters.class);

    Charset encoding = CmdLineUtil.getEncodingParameter(args);

    if (encoding == null) {
      throw new TerminateToolException(1);
    }
    
    ADChunkSampleStream sampleStream = new ADChunkSampleStream(CmdLineUtil.openInFile(new File(params
        .getData())), encoding.name());

    if(params.getStart() != null && params.getStart() > -1) {
      sampleStream.setStart(params.getStart());
    }
    
    if(params.getEnd() != null && params.getEnd() > -1) {
      sampleStream.setEnd(params.getEnd());
    }
    
    return sampleStream;
  }
}
