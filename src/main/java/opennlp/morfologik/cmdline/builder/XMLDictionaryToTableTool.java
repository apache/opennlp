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

package opennlp.morfologik.cmdline.builder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Iterator;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.postag.POSDictionary;

public class XMLDictionaryToTableTool extends BasicCmdLineTool {

  interface Params extends XMLDictionaryToTableParams {
  }

  public String getShortDescription() {
    return "reads an OpenNLP XML tag dictionary and outputs it in a tab separated file";
  }

  public String getHelp() {
    return getBasicHelp(Params.class);
  }

  public void run(String[] args) {
    Params params = validateAndParseParams(args, Params.class);

    File dictInFile = params.getInputFile();
    File dictOutFile = params.getOutputFile();
    Charset encoding = params.getEncoding();

    CmdLineUtil.checkInputFile("dictionary input file", dictInFile);
    CmdLineUtil.checkOutputFile("dictionary output file", dictOutFile);

    POSDictionary tagDictionary = null;
    try {
      tagDictionary = POSDictionary.create(new FileInputStream(dictInFile));
    } catch (IOException e) {
      throw new TerminateToolException(-1,
          "Error while loading XML POS Dictionay: " + e.getMessage(), e);
    }
    Iterator<String> iterator = tagDictionary.iterator();

    try (BufferedWriter writer = Files.newBufferedWriter(dictOutFile.toPath(),
        encoding)) {
      while (iterator.hasNext()) {
        String word = iterator.next();
        String wordAndLemma = word + "\t\t"; // lemma is empty
        for (String tag : tagDictionary.getTags(word)) {
          writer.write(wordAndLemma + tag);
          writer.newLine();
        }
      }
      writer.close();
    } catch (IOException e) {
      throw new TerminateToolException(-1, "Error while writing output: "
          + e.getMessage(), e);
    }
  }

}
