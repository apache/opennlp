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

package opennlp.tools.cmdline.tokenizer;

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.TerminateToolException;

public final class SimpleTokenizerTool implements CmdLineTool {

  public String getName() {
    return "SimpleTokenizer";
  }
  
  public String getShortDescription() {
    return "character class tokenizer";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " < sentences";
  }

  public void run(String[] args) {
    if (args.length != 0) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    CommandLineTokenizer tokenizer = 
      new CommandLineTokenizer(opennlp.tools.tokenize.SimpleTokenizer.INSTANCE);
    
    tokenizer.process();
  }
}
