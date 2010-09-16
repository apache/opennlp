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
 *
 * --------------------------------------------------------------------------
 * Data for the US Census and names can be found here for the 1990 Census:
 * http://www.census.gov/genealogy/names/names_files.html
 */

package opennlp.tools.cmdline.namefind;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Locale;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.BasicTrainingParameters;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.formats.NameFinderCensus90NameStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.StringList;

/**
 * This tool helps create a loadable dictionary for the namefinder, from
 * data collected from US Census data.
 * 
 * @author <a href="mailto:james.kosin.04@cnu.edu">James Kosin</a>
 * @version $Revision: 1.2 $, $Date: 2010-09-16 03:08:38 $
 */
public class CensusDictionaryCreatorTool implements CmdLineTool {

    public String getName() {
        return "CensusDictionaryCreator";
    }

    public String getShortDescription() {
        return "Converts 1990 US Census names into a dictionary";
    }

    public String getHelp() {
        return "Usage: " + CLI.CMD + " " + getName() + " " +
            BasicTrainingParameters.getParameterUsage() + " nameData dictionary\n" +
            BasicTrainingParameters.getDescription();
    }

  static ObjectStream<StringList> openSampleData(String sampleDataName,
                                                 File sampleDataFile, Charset encoding) {
        CmdLineUtil.checkInputFile(sampleDataName + " Data", sampleDataFile);

        FileInputStream sampleDataIn = CmdLineUtil.openInFile(sampleDataFile);

        return new NameFinderCensus90NameStream(sampleDataIn, encoding.toString());
  }

  protected Dictionary createDictionary(ObjectStream<StringList> sampleStream) throws IOException {
      Dictionary mNameDictionary = new Dictionary(true);
      StringList entry;

      System.out.println("Creating Dictionary...");
      entry = sampleStream.read();
      while (entry != null) {
          if (!mNameDictionary.contains(entry)) {
              mNameDictionary.put(entry);
          }
          entry = sampleStream.read();
      }
      try {
          sampleStream.close();
      } catch(IOException e) {
          // sorry this can happen.
      }

      return mNameDictionary;
  }

  public void run(String[] args) {
      // expecting arguments -lang en -encoding utf8 namefile dictionary
        if (args.length < 6) {
            System.err.println(getHelp());
            throw new TerminateToolException(1);
        }

        BasicTrainingParameters parameters = new BasicTrainingParameters(args);

        if(!parameters.isValid()) {
          System.err.println(getHelp());
          throw new TerminateToolException(1);
        }

        File testData = new File(args[(args.length - 2)]);
        File dictOutFile = new File(args[(args.length - 1)]);

        CmdLineUtil.checkInputFile("Name data", testData);
        CmdLineUtil.checkOutputFile("Dictionary file", dictOutFile);
        Dictionary mDictionary;

        try {
            mDictionary = createDictionary(openSampleData("Name", testData,
                                                          parameters.getEncoding()));
        } catch (IOException e) {
            CmdLineUtil.printTrainingIoError(e);
            throw new TerminateToolException(-1);
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
