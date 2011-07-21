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

package opennlp.tools.cmdline.dictionary;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.dictionary.Dictionary;

public class DictionaryBuilderTool implements CmdLineTool {

  interface Params extends DictionaryBuilderParams {

  }

  public String getName() {
    return "DictionaryBuilder";
  }

  public String getShortDescription() {
    return "builds a new dictionary";
  }

  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " "
        + ArgumentParser.createUsage(Params.class);

  }

  public void run(String[] args) {
    if (!ArgumentParser.validateArguments(args, Params.class)) {
      System.err.println(getHelp());
      throw new TerminateToolException(1);
    }

    Params params = ArgumentParser.parse(args, Params.class);

    File dictInFile = params.getInputFile();
    File dictOutFile = params.getOutputFile();
    Charset encoding = params.getEncoding();

    CmdLineUtil.checkInputFile("dictionary input file", dictInFile);
    CmdLineUtil.checkOutputFile("dictionary output file", dictOutFile);

    InputStreamReader in = null;
    OutputStream out = null;
    try {
      in = new InputStreamReader(new FileInputStream(dictInFile), encoding);
      out = new FileOutputStream(dictOutFile);

      Dictionary dict = Dictionary.parseOneEntryPerLine(in);
      dict.serialize(out);

    } catch (IOException e) {
      CmdLineUtil.printTrainingIoError(e);
      throw new TerminateToolException(-1);
    } finally {
      try {
        in.close();
        out.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

  }

}
