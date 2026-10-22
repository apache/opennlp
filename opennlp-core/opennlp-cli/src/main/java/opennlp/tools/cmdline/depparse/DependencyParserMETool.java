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

package opennlp.tools.cmdline.depparse;

import java.io.File;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.depparse.DependencyParserME;

/** Parses POS-tagged sentences from standard input and prints basic CoNLL-U trees. */
public class DependencyParserMETool extends BasicCmdLineTool {

  /** {@inheritDoc} */
  @Override
  public String getShortDescription() {
    return "Parses dependency trees from POS-tagged sentences";
  }

  /** {@inheritDoc} */
  @Override
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " model < tagged-sentences";
  }

  /** {@inheritDoc} */
  @Override
  public void run(String[] args) {
    if (args.length != 1) {
      throw new TerminateToolException(1, getHelp());
    }
    DependencyParserToolSupport.parseTaggedSentences(
        new DependencyParserME(new DependencyModelLoader().load(new File(args[0]))));
  }
}
