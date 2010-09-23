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
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.ObjectStreamFactory;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.WordTagSampleStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class WordTagSampleStreamFactory implements ObjectStreamFactory<POSSample> {

  static interface Parameters {
    
    @ParameterDescription(valueName = "sampleData")
    String getData();
    
    @ParameterDescription(valueName = "charsetName")
    String getEncoding();
  }
  
  public String getUsage() {
    return ArgumentParser.createUsage(Parameters.class);
  }

  public boolean validateArguments(String[] args) {
    return ArgumentParser.validateArguments(args, Parameters.class);
  }

  ObjectStream<POSSample> create(Parameters params) {
    ObjectStream<String> lineStream;
    try {
      lineStream = new PlainTextByLineStream(new InputStreamReader(
          CmdLineUtil.openInFile(new File(params.getData())), params.getEncoding()));
      
      return new WordTagSampleStream(lineStream);
    } catch (UnsupportedEncodingException e) {
      System.err.println("Encoding not supported: " + params.getEncoding());
      throw new TerminateToolException(-1);
    }
  }
  
  public ObjectStream<POSSample> create(String[] args) {
    Parameters params = ArgumentParser.parse(args, Parameters.class);
    return create(params);
  }
}
