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
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.ObjectStreamFactory;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.postag.POSSample;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.tokenize.DetokenizationDictionary;
import opennlp.tools.tokenize.Detokenizer;
import opennlp.tools.tokenize.DictionaryDetokenizer;
import opennlp.tools.util.ObjectStream;

public class ConllXSentenceSampleStreamFactory implements ObjectStreamFactory<SentenceSample> {

  // which params do we need ?
  
  interface Parameters extends ConllXPOSSampleStreamFactory.Parameters {
    
    // TODO:
    // Make chunk size configurable
    
    @ParameterDescription(valueName = "dictionary")
    String getDetokenizer();
  }
  
  @Override
  public String getUsage() {
    return ArgumentParser.createUsage(Parameters.class);
  }

  @Override
  public boolean validateArguments(String[] args) {
    return ArgumentParser.validateArguments(args, Parameters.class);
  }

  @Override
  public ObjectStream<SentenceSample> create(String[] args) {
    
    Parameters params = ArgumentParser.parse(args, Parameters.class);
    
    // TODO: Compare code to ConllXTokenSampleStream, maybe it can be shared somehow
    
    ObjectStream<POSSample> posSampleStream = 
        new ConllXPOSSampleStreamFactory().create(params);
    
    Detokenizer detokenizer;
    try {
      detokenizer = new DictionaryDetokenizer(new DetokenizationDictionary(new FileInputStream(new File(params.getDetokenizer()))));
    } catch (IOException e) {
      System.err.println("Error while loading detokenizer dict: " + e.getMessage());
      throw new TerminateToolException(-1);
    }
    
    return new POSToSentenceSampleStream(detokenizer, posSampleStream, 30);
  }
}
