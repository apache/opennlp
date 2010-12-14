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
import java.io.FileInputStream;
import java.io.IOException;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.ObjectStreamFactory;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.DetokenizerParameter;
import opennlp.tools.postag.POSSample;
import opennlp.tools.tokenize.DetokenizationDictionary;
import opennlp.tools.tokenize.Detokenizer;
import opennlp.tools.tokenize.DictionaryDetokenizer;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.ObjectStream;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class ConllXTokenSampleStreamFactory implements ObjectStreamFactory<TokenSample> {
  
  interface Parameters extends ConllXPOSSampleStreamFactory.Parameters, DetokenizerParameter {
  }
  
  public String getUsage() {
    return ArgumentParser.createUsage(Parameters.class);
  }

  public boolean validateArguments(String[] args) {
    return ArgumentParser.validateArguments(args, Parameters.class);
  }

  public ObjectStream<TokenSample> create(String[] args) {
    
    Parameters params = ArgumentParser.parse(args, Parameters.class);
    
    ObjectStream<POSSample> samples = new ConllXPOSSampleStreamFactory().create(params);
    
    Detokenizer detokenizer;
    try {
      detokenizer = new DictionaryDetokenizer(new DetokenizationDictionary(new FileInputStream(new File(params.getDetokenizer()))));
    } catch (IOException e) {
      System.err.println("Error while loading detokenizer dict: " + e.getMessage());
      throw new TerminateToolException(-1);
    }
    
    return new POSToTokenSampleStream(detokenizer,samples);
  }
}
