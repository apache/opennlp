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
import java.io.OutputStream;
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
 * This tool helps create a loadable dictionary for the {@code NameFinder},
 * from data collected from US Census data.
 * <p>
 * Data for the US Census and names can be found here for the 1990 Census:
 * <br>
 * <a href="http://www.census.gov/genealogy/names/names_files.html">www.census.gov</a>
 */
public class CensusDictionaryCreatorTool implements CmdLineTool {

  /**
   * Create a list of expected parameters.
   */
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

  /**
   * Gets the name for the tool.
   *
   * @return {@code String}  a name to be used to call this class.
   */
  public String getName() {

    return "CensusDictionaryCreator";
  }

  /**
   * Gets a short description for the tool.
   *
   * @return {@code String}  a short description describing the purpose of
   *    the tool to the user.
   */
  public String getShortDescription() {

    return "Converts 1990 US Census names into a dictionary";
  }

  /**
   * Gets the expected usage of the tool as an example.
   *
   * @return {@code String}  a descriptive example on how to properly call
   *    the tool from the command line.
   */
  public String getHelp() {

    return "Usage: " + CLI.CMD + " " + getName() + " " + ArgumentParser.createUsage(Parameters.class);
  }

  /**
   * 
   * @param sampleStream
   * @return a {@code Dictionary} class containing the name dictionary
   *    built from the input file.
   * @throws IOException
   */
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

  /**
   * This method is much like the old main() method used in prior class
   * construction, and allows another main class to run() this classes method
   * to perform the operations.
   *
   * @param args  a String[] array of arguments passed to the run method
   */
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

    FileInputStream sampleDataIn = CmdLineUtil.openInFile(testData);
    ObjectStream<StringList> sampleStream = new NameFinderCensus90NameStream(sampleDataIn, 
        Charset.forName(params.getEncoding()));
    
    Dictionary mDictionary;
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
    
    OutputStream out = null;
    
    try {
      out = new FileOutputStream(dictOutFile);
      mDictionary.serialize(out);
    } catch (IOException ex) {
      System.err.println("Error during write to dictionary file: " + ex.getMessage());
      throw new TerminateToolException(-1);
    }
    finally {
      if (out != null)
        try {
          out.close();
        } catch (IOException e) {
          // file might be damaged
          System.err.println("Attention: Failed to correctly write dictionary:");
          System.err.println(e.getMessage());
          throw new TerminateToolException(-1);
        }
    }
  }
}
