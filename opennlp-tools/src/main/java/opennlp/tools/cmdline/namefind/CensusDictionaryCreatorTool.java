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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.formats.NameFinderCensus90NameStream;
import opennlp.tools.util.InputStreamFactory;
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
public class CensusDictionaryCreatorTool extends BasicCmdLineTool {

  /**
   * Create a list of expected parameters.
   */
  interface Parameters {

    @ParameterDescription(valueName = "code")
    @OptionalParameter(defaultValue = "en")
    String getLang();

    @ParameterDescription(valueName = "charsetName")
    @OptionalParameter(defaultValue = "UTF-8")
    String getEncoding();

    @ParameterDescription(valueName = "censusDict")
    String getCensusData();

    @ParameterDescription(valueName = "dict")
    String getDict();
  }

  public String getShortDescription() {
    return "Converts 1990 US Census names into a dictionary";
  }


  public String getHelp() {
    return getBasicHelp(Parameters.class);
  }

  /**
   * Creates a dictionary.
   *
   * @param sampleStream stream of samples.
   * @return a {@code Dictionary} class containing the name dictionary
   *     built from the input file.
   * @throws IOException IOException
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

  public void run(String[] args) {
    Parameters params = validateAndParseParams(args, Parameters.class);

    File testData = new File(params.getCensusData());
    File dictOutFile = new File(params.getDict());

    CmdLineUtil.checkInputFile("Name data", testData);
    CmdLineUtil.checkOutputFile("Dictionary file", dictOutFile);

    InputStreamFactory sampleDataIn = CmdLineUtil.createInputStreamFactory(testData);

    Dictionary mDictionary;
    try (
        ObjectStream<StringList> sampleStream = new NameFinderCensus90NameStream(
            sampleDataIn, Charset.forName(params.getEncoding()))) {
      System.out.println("Creating Dictionary...");
      mDictionary = createDictionary(sampleStream);
    } catch (IOException e) {
      throw new TerminateToolException(-1, "IO error while reading training data or indexing data: "
          + e.getMessage(), e);
    }

    System.out.println("Saving Dictionary...");

    try (OutputStream out = new FileOutputStream(dictOutFile)) {
      mDictionary.serialize(out);
    } catch (IOException e) {
      throw new TerminateToolException(-1, "IO error while writing dictionary file: "
          + e.getMessage(), e);
    }
  }
}
