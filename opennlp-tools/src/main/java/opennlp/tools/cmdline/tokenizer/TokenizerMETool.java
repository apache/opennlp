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

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.tokenize.TokenizerModel;

public final class TokenizerMETool extends BasicCmdLineTool {
  private static final Logger logger = LoggerFactory.getLogger(TokenizerMETool.class);

  @Override
  public String getShortDescription() {
    return "Learnable tokenizer";
  }

  @Override
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " model < sentences";
  }

  @Override
  public void run(String[] args) {
    if (args.length != 1) {
      logger.info(getHelp());
    } else {

      TokenizerModel model = new TokenizerModelLoader().load(new File(args[0]));

      CommandLineTokenizer tokenizer =
          new CommandLineTokenizer(new opennlp.tools.tokenize.TokenizerME(model));

      tokenizer.process();
    }
  }
}
