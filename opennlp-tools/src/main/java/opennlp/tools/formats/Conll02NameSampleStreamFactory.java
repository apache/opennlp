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

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.ObjectStreamFactory;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.formats.Conll02NameSampleStream.LANGUAGE;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class Conll02NameSampleStreamFactory implements ObjectStreamFactory<NameSample> {
  
  interface Parameters {
    @ParameterDescription(valueName = "es|nl")
    String getLang();
    
    @ParameterDescription(valueName = "sampleData")
    String getData();
    
    @ParameterDescription(valueName = "per,loc,org,misc")
    String getTypes();
  }
  
  public String getUsage() {
    return ArgumentParser.createUsage(Parameters.class);
  }
  
  public boolean validateArguments(String[] args) {
    return ArgumentParser.validateArguments(args, Parameters.class);
  }
  
  public ObjectStream<NameSample> create(String[] args) {
    
    Parameters params = ArgumentParser.parse(args, Parameters.class);
    
    LANGUAGE lang;
    if ("nl".equals(params.getLang())) {
      lang = LANGUAGE.NL;
    }
    else if ("es".equals(params.getLang())) {
      lang = LANGUAGE.ES;
    }
    else {
      System.err.println("Unsupported language: " + params.getLang());
      throw new TerminateToolException(-1);
    }
    
    int typesToGenerate = 0;
    
    if (params.getTypes().contains("per")) {
      typesToGenerate = typesToGenerate | 
          Conll02NameSampleStream.GENERATE_PERSON_ENTITIES;
    }
    if (params.getTypes().contains("org")) {
      typesToGenerate = typesToGenerate | 
          Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES;
    }
    if (params.getTypes().contains("loc")) {
      typesToGenerate = typesToGenerate | 
          Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES;
    }
    if (params.getTypes().contains("misc")) {
      typesToGenerate = typesToGenerate | 
          Conll02NameSampleStream.GENERATE_MISC_ENTITIES;
    }

    
    return new Conll02NameSampleStream(lang,
        CmdLineUtil.openInFile(new File(params.getData())), typesToGenerate);
  }
}
