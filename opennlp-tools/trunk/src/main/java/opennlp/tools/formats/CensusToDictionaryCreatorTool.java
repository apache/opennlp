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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.BasicTrainingParameters;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.StringList;

/**
 * This tool helps create a loadable dictionary for the namefinder, from
 * data collected from US Census data.
 * 
 * @author <a href="mailto:james.kosin.04@cnu.edu">James Kosin</a>
 * @version $Revision: 1.1 $, $Date: 2010-08-24 00:42:22 $
 */
public class CensusToDictionaryCreatorTool implements CmdLineTool {

    public String getName() {
        return "TokenNameFinderDictionaryCreator";
    }

    public String getShortDescription() {
        return "";
    }

    public String getHelp() {
        return "Usage: " + CLI.CMD + " " + getName() + " " +
            BasicTrainingParameters.getParameterUsage() + " nameData dictionary\n" +
            BasicTrainingParameters.getDescription();
    }

  static ObjectStream<String> openSampleData(String sampleDataName,
      File sampleDataFile, Charset encoding) {
        CmdLineUtil.checkInputFile(sampleDataName + " Data", sampleDataFile);

        FileInputStream sampleDataIn = CmdLineUtil.openInFile(sampleDataFile);

        return new PlainTextByLineStream(sampleDataIn.getChannel(), encoding);
  }

  public void run(String[] args) {
        if (args.length < 6) {
            System.out.println(getHelp());
            throw new TerminateToolException(1);
        }

        BasicTrainingParameters parameters = new BasicTrainingParameters(args);

        if(!parameters.isValid()) {
          System.out.println(getHelp());
          throw new TerminateToolException(1);
        }

        File testData = new File(args[(args.length - 2)]);
        File dictOutFile = new File(args[(args.length - 1)]);

        CmdLineUtil.checkInputFile("Name data", testData);
        CmdLineUtil.checkOutputFile("Dictionary file", dictOutFile);

        ObjectStream<String> sampleStream =
            openSampleData("Name", testData, parameters.getEncoding());
        Dictionary mDictionary = new Dictionary(true);
        Locale loc = new Locale(parameters.getLanguage());

        try {
            String line = sampleStream.read();
            System.out.println("Creating Dictionary...");
            while ((line != null) &&
                   !line.isEmpty()) {
                // the data is in ALL CAPS and needs to be stripped from
                // other data on the line.
                String[] parse = line.split(" ");
                String name = parse[0].toLowerCase(loc);
                String name2;
                // now we need to re-introduce caps back into the name
                // no-one ever has a lower case name.  However, there are a few
                // subtle exceptions to every rule.  Mc is a common exception
                // where the name will have two caps letters.  McDonald, McLee
                // we test for a length of greater than 2 because there is one
                // name that is Mc in the data.
                if ((name.length() > 2) &&
                    name.startsWith("mc")) {
                    // this tranlates the case for McDonald, etc.
                    name2 = name.substring(0,1).toUpperCase(loc) +
                            name.substring(1,2) +
                            name.substring(2,3).toUpperCase(loc) +
                            name.substring(3);
                } else {
                    // this keeps the first letter of the name capital
                    name2 = name.substring(0,1).toUpperCase(loc) + name.substring(1);
                }

                StringList entry = new StringList(new String[]{name2});

                if (!mDictionary.contains(entry)) {
                    // todo: remove debug output statement
                    System.out.println(entry);
                    mDictionary.put(entry);
                }
                line = sampleStream.read();
            }
        } catch (IOException e) {
            CmdLineUtil.printTrainingIoError(e);
            throw new TerminateToolException(-1);
        } finally {
            try {
                sampleStream.close();
            } catch (IOException e) {
                // sorry this can fail.
            }
        }
        System.out.println("Saving Dictionary...");
        try {
            mDictionary.serialize(new FileOutputStream(dictOutFile));
        } catch (IOException ex) {
            Logger.getLogger(CensusToDictionaryCreatorTool.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
