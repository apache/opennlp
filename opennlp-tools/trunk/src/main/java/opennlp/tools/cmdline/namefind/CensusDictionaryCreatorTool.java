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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.formats.NameFinderCensus90NameStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.StringList;

/**
 * This tool helps create a loadable dictionary for the namefinder, from
 * data collected from US Census data.
 * 
 *
 * --------------------------------------------------------------------------
 * Data for the US Census and names can be found here for the 1990 Census:
 * http://www.census.gov/genealogy/names/names_files.html
 * --------------------------------------------------------------------------
 * 
 * @author <a href="mailto:james.kosin.04@cnu.edu">James Kosin</a>
 * @version $Revision: 1.4 $, $Date: 2010-09-17 03:07:44 $
 */
public class CensusDictionaryCreatorTool implements CmdLineTool {

  interface Parameters {
    
    @ParameterDescription(valueName = "code")
    @OptionalParameter(defaultValue = "en")
    String getLang();
    
    @ParameterDescription(valueName = "charsetName")
    @OptionalParameter(defaultValue="UTF-8")
    String getEncoding();
    
    @ParameterDescription(valueName = "censusDict")
    String getCensusData();
    
    @ParameterDescription(valueName = "dict")
    String getDict();
  }
  
  public String getName() {

    return "CensusDictionaryCreator";
  }

  public String getShortDescription() {

    return "Converts 1990 US Census names into a dictionary";
  }

  public String getHelp() {

    return "Usage: " + CLI.CMD + " " + getName() + " " + ArgumentParser.createUsage(Parameters.class);
  }

  static ObjectStream<StringList> openSampleData(String sampleDataName,
                                               File sampleDataFile, Charset encoding) {

    CmdLineUtil.checkInputFile(sampleDataName + " Data", sampleDataFile);
    FileInputStream sampleDataIn = CmdLineUtil.openInFile(sampleDataFile);
    return new NameFinderCensus90NameStream(sampleDataIn, encoding);
  }

  public static Dictionary createDictionary(ObjectStream<StringList> sampleStream) throws IOException {

    Dictionary mNameDictionary = new Dictionary(true);
    StringList entry;

    entry = sampleStream.read();
    while (entry != null) {
      if (!mNameDictionary.contains(entry)) {
        mNameDictionary.put(entry);
      }
      entry = sampleStream.read();
    }

    return mNameDictionary;
  }

  public void run(String[] args) {

    if (!ArgumentParser.validateArguments(args, Parameters.class)) {
      System.err.println(getHelp());
      throw new TerminateToolException(1);
    }

    Parameters params = ArgumentParser.parse(args, Parameters.class);

    File testData = new File(params.getCensusData());
    File dictOutFile = new File(params.getDict());

    CmdLineUtil.checkInputFile("Name data", testData);
    CmdLineUtil.checkOutputFile("Dictionary file", dictOutFile);
    Dictionary mDictionary;
    ObjectStream<StringList> sampleStream = openSampleData("Name", testData,
            Charset.forName(params.getEncoding()));

    try {
      System.out.println("Creating Dictionary...");
      mDictionary = createDictionary(sampleStream);
    } catch (IOException e) {
      CmdLineUtil.printTrainingIoError(e);
      throw new TerminateToolException(-1);
    } finally {
      try {
        sampleStream.close();
      } catch(IOException e) {
        // sorry this can fail..
      }
    }

    System.out.println("Saving Dictionary...");
    try {
      mDictionary.serialize(new FileOutputStream(dictOutFile));
    } catch (IOException ex) {
      System.err.println("Error during write to dictionary file: " + ex.getMessage());
      throw new TerminateToolException(-1);
    }
  }

}
