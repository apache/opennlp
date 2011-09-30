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
import java.io.IOException;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.ObjectStreamFactory;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.util.ObjectStream;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class LeipzigDocumentSampleStreamFactory implements ObjectStreamFactory<DocumentSample> {

  interface Parameters {
    @ParameterDescription(valueName = "languageCode")
    String getLang();
    
    @ParameterDescription(valueName = "sampleData")
    String getData();
  }
  
  public String getUsage() {
    return ArgumentParser.createUsage(Parameters.class);
  }
  
  public boolean validateArguments(String[] args) {
    return ArgumentParser.validateArguments(args, Parameters.class);
  }
  
  public ObjectStream<DocumentSample> create(String[] args) {
    
    Parameters params = ArgumentParser.parse(args, Parameters.class);

    try {
      return new LeipzigDoccatSampleStream(params.getLang(), 20,
          CmdLineUtil.openInFile(new File(params.getData())));
    } catch (IOException e) {
      System.err.println("Cannot open sample data: " + e.getMessage());
      throw new TerminateToolException(-1);
    }
  }
}
