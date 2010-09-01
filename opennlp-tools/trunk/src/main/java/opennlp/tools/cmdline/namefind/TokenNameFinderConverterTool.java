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

package opennlp.tools.cmdline.namefind;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.formats.Conll02NameSampleStream;
import opennlp.tools.formats.Conll02NameSampleStream.LANGUAGE;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;

/**
 * Tool to convert multiple data formats into native opennlp name finder training
 * format.
 */
public class TokenNameFinderConverterTool implements CmdLineTool {

  public String getName() {
    return "TokenNameFinderConverter";
  }

  public String getShortDescription() {
    return "converts foreign data formats to native format";
  }

  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " -format conll02 -lang es|nl -types per,loc,org,misc -data sampleData";
  }
  
  public void run(String[] args) {
    
    if (args.length != 8) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    String formatType = CmdLineUtil.getParameter("-format", args);
    
    String language = CmdLineUtil.getParameter("-lang", args);
    
    String types = CmdLineUtil.getParameter("-types", args);
    
    File sampleData = new File(CmdLineUtil.getParameter("-data", args));
    
    ObjectStream<NameSample> sampleStream = null;
    
    try {
      
      if ("conll02".equals(formatType)) {
        
        LANGUAGE lang;
        if ("nl".equals(language)) {
          lang = LANGUAGE.NL;
        }
        else if ("es".equals(language)) {
          lang = LANGUAGE.ES;
        }
        else {
          System.err.println("Unsupported language: " + language);
          throw new TerminateToolException(-1);
        }
        
        int typesToGenerate = 0;
        
        if (types.contains("per")) {
          typesToGenerate = typesToGenerate | 
              Conll02NameSampleStream.GENERATE_PERSON_ENTITIES;
        }
        else if (types.contains("org")) {
          typesToGenerate = typesToGenerate | 
              Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES;
        }
        else if (types.contains("loc")) {
          typesToGenerate = typesToGenerate | 
              Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES;
        }
        else if (types.contains("misc")) {
          typesToGenerate = typesToGenerate | 
              Conll02NameSampleStream.GENERATE_MISC_ENTITIES;
        }
        
        InputStream in = CmdLineUtil.openInFile(sampleData);
        
        sampleStream = new Conll02NameSampleStream(lang, in, typesToGenerate);
      }
      else {
        System.err.println("Unkown format: " + formatType);
        throw new TerminateToolException(-1);
      }
      
      NameSample sample;
      while((sample = sampleStream.read()) != null) {
        System.out.println(sample.toString());
      }
    }
    catch (IOException e) {
      CmdLineUtil.printTrainingIoError(e);
      throw new TerminateToolException(-1);
    }
    finally {
      if (sampleStream != null)
        try {
          sampleStream.close();
        } catch (IOException e) {
          // sorry that this can fail
        }
    }
  }
}
