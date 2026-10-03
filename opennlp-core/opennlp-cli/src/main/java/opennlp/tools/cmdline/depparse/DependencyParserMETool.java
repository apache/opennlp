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
import java.io.IOException;
import java.io.PrintWriter;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.SystemInputStreamFactory;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.depparse.DependencyGraph;
import opennlp.tools.depparse.DependencyParserME;
import opennlp.tools.postag.POSSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.StringUtil;

/** Parses POS-tagged sentences from standard input and prints basic CoNLL-U trees. */
public class DependencyParserMETool extends BasicCmdLineTool {

  /** Separates the fields of a CoNLL-U token row. */
  private static final String FIELD_SEPARATOR = "\t";

  /** Fills a CoNLL-U field that the parser does not predict. */
  private static final String EMPTY_FIELD = "_";

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
    DependencyParserME parser = new DependencyParserME(new DependencyModelLoader().load(new File(args[0])));
    PrintWriter out = new PrintWriter(System.out, true);
    try (ObjectStream<String> lines = new PlainTextByLineStream(
        new SystemInputStreamFactory(), SystemInputStreamFactory.encoding())) {
      String line;
      while ((line = lines.read()) != null) {
        if (StringUtil.isBlank(line)) {
          continue;
        }
        POSSample sample = POSSample.parse(line);
        String[] tokens = sample.getSentence();
        String[] tags = sample.getTags();
        DependencyGraph graph = parser.parse(tokens, tags);
        for (int i = 0; i < tokens.length; i++) {
          // Input tags may be UPOS or XPOS; retain them in XPOS without guessing a mapping.
          out.println(String.join(FIELD_SEPARATOR, Integer.toString(i + 1), tokens[i],
              EMPTY_FIELD, EMPTY_FIELD, tags[i], EMPTY_FIELD,
              Integer.toString(graph.headOf(i) + 1), graph.relationOf(i),
              EMPTY_FIELD, EMPTY_FIELD));
        }
        out.println();
      }
    } catch (IOException e) {
      CmdLineUtil.handleStdinIoError(e);
    }
  }
}
