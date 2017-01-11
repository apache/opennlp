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

package opennlp.tools.cmdline.entitylinker;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.SystemInputStreamFactory;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.entitylinker.EntityLinker;
import opennlp.tools.entitylinker.EntityLinkerFactory;
import opennlp.tools.entitylinker.EntityLinkerProperties;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

public class EntityLinkerTool extends BasicCmdLineTool {

  @Override
  public String getShortDescription() {
    return "links an entity to an external data set";
  }

  @Override
  public void run(String[] args) {

    if (0 == args.length) {
      System.out.println(getHelp());
    }
    else {
      // TODO: Ask Mark if we can remove the type, the user knows upfront if he tries
      // to link place names or company mentions ...
      String entityType = "location";

      // Load the properties, they should contain everything that is necessary to instantiate
      // the component

      // TODO: Entity Linker Properties constructor should not duplicate code
      EntityLinkerProperties properties;
      try {
        properties = new EntityLinkerProperties(new File(args[0]));
      }
      catch (IOException e) {
        throw new TerminateToolException(-1, "Failed to load the properties file!");
      }

      // TODO: It should not just throw Exception.

      EntityLinker entityLinker;
      try {
        entityLinker = EntityLinkerFactory.getLinker(entityType, properties);
      }
      catch (Exception e) {
        throw new TerminateToolException(-1, "Failed to instantiate the Entity Linker: " + e.getMessage());
      }

      PerformanceMonitor perfMon = new PerformanceMonitor(System.err, "sent");
      perfMon.start();

      try {

        ObjectStream<String> untokenizedLineStream = new PlainTextByLineStream(
            new SystemInputStreamFactory(), SystemInputStreamFactory.encoding());

        List<NameSample> document = new ArrayList<>();

        String line;
        while ((line = untokenizedLineStream.read()) != null) {

          if (line.trim().isEmpty()) {
            // Run entity linker ... and output result ...

            StringBuilder text = new StringBuilder();
            Span sentences[] = new Span[document.size()];
            Span[][] tokensBySentence = new Span[document.size()][];
            Span[][] namesBySentence = new Span[document.size()][];

            for (int i = 0; i < document.size(); i++) {

              NameSample sample = document.get(i);

              namesBySentence[i] = sample.getNames();

              int sentenceBegin = text.length();

              Span[] tokens = new Span[sample.getSentence().length];

              // for all tokens
              for (int ti = 0; ti < sample.getSentence().length; ti++) {
                int tokenBegin = text.length();
                text.append(sample.getSentence()[ti]);
                text.append(" ");
                tokens[ti] = new Span(tokenBegin, text.length());
              }

              tokensBySentence[i] = tokens;

              sentences[i] = new Span(sentenceBegin, text.length());
              text.append("\n");
            }

            List<Span> linkedSpans =
                entityLinker.find(text.toString(), sentences, tokensBySentence, namesBySentence);

            for (int i = 0; i < linkedSpans.size(); i++) {
              System.out.println(linkedSpans.get(i));
            }

            perfMon.incrementCounter(document.size());
            document.clear();
          }
          else {
            document.add(NameSample.parse(line, false));
          }
        }
      }
      catch (IOException e) {
        CmdLineUtil.handleStdinIoError(e);
      }

      perfMon.stopAndPrintFinalResult();
    }
  }

  @Override
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " model < sentences";
  }
}
