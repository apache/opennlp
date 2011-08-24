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
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;

public class BioNLP2004NameSampleStreamFactory
    implements ObjectStreamFactory<NameSample>{

  interface Parameters {
    @ParameterDescription(valueName = "sampleData")
    String getData();
    
    @ParameterDescription(valueName = "DNA,protein,cell_type,cell_line,RNA")
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
    int typesToGenerate = 0;
    
    if (params.getTypes().contains("DNA")) {
      typesToGenerate = typesToGenerate | 
          BioNLP2004NameSampleStream.GENERATE_DNA_ENTITIES;
    }
    else if (params.getTypes().contains("protein")) {
      typesToGenerate = typesToGenerate | 
          BioNLP2004NameSampleStream.GENERATE_PROTEIN_ENTITIES;
    }
    else if (params.getTypes().contains("cell_type")) {
      typesToGenerate = typesToGenerate | 
          BioNLP2004NameSampleStream.GENERATE_CELLTYPE_ENTITIES;
    }
    else if (params.getTypes().contains("cell_line")) {
      typesToGenerate = typesToGenerate | 
          BioNLP2004NameSampleStream.GENERATE_CELLLINE_ENTITIES;
    }
    else if (params.getTypes().contains("RNA")) {
      typesToGenerate = typesToGenerate | 
          BioNLP2004NameSampleStream.GENERATE_RNA_ENTITIES;
    }

    return new BioNLP2004NameSampleStream(
        CmdLineUtil.openInFile(new File(params.getData())), typesToGenerate);
  }
}
