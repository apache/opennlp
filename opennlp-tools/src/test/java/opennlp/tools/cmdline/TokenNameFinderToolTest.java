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

package opennlp.tools.cmdline;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import opennlp.tools.cmdline.namefind.TokenNameFinderTool;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleDataStream;
import opennlp.tools.namefind.TokenNameFinderFactory;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.MockInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TokenNameFinderToolTest {

  /*
   * Programmatic change to debug log to ensure that we can see log messages to
   * confirm no duplicate download is happening
   */
  @BeforeAll
  public static void prepare() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger logger = context.getLogger("opennlp.tools.cmdline.namefind");
    logger.setLevel(Level.INFO);
  }

  /*
   * Programmatic restore the default log level (= OFF) after the test
   */
  @AfterAll
  public static void cleanup() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger logger = context.getLogger("opennlp.tools.cmdline.namefind");
    logger.setLevel(Level.OFF);
  }

  @Test
  void run() throws IOException {
    try (LogCaptor logCaptor = LogCaptor.forClass(TokenNameFinderTool.class)) {
      File model1 = trainModel();
      String[] args = new String[] {model1.getAbsolutePath()};

      final String in = "It is Stefanie Schmidt.\n";
      InputStream stream = new ByteArrayInputStream(in.getBytes(StandardCharsets.UTF_8));

      System.setIn(stream);

      TokenNameFinderTool tool = new TokenNameFinderTool();
      tool.run(args);

      assertEquals(1, logCaptor.getInfoLogs().size());
      final String content = logCaptor.getInfoLogs().get(0);
      logCaptor.clearLogs();
      assertEquals("It is <START:person> Stefanie Schmidt. <END>", content);
      assertTrue(model1.delete());
    }
  }

  @Test
  void invalidModel() {
    assertThrows(TerminateToolException.class, () -> {
      String[] args = new String[] {"invalidmodel.bin"};
      TokenNameFinderTool tool = new TokenNameFinderTool();
      tool.run(args);

    });
  }

  @Test
  void usage() {
    try (LogCaptor logCaptor = LogCaptor.forClass(TokenNameFinderTool.class)) {
      String[] args = new String[] {};

      TokenNameFinderTool tool = new TokenNameFinderTool();
      tool.run(args);

      assertEquals(1, logCaptor.getInfoLogs().size());
      final String content = logCaptor.getInfoLogs().get(0);
      assertEquals(tool.getHelp(), content.trim());
    }
  }

  private File trainModel() throws IOException {
    ObjectStream<String> lineStream =
        new PlainTextByLineStream(new MockInputStreamFactory(
            new File("opennlp/tools/namefind/AnnotatedSentencesWithTypes.txt")),
            StandardCharsets.ISO_8859_1);

    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ITERATIONS_PARAM, 70);
    params.put(TrainingParameters.CUTOFF_PARAM, 1);

    TokenNameFinderModel model;
    TokenNameFinderFactory nameFinderFactory = new TokenNameFinderFactory();

    try (ObjectStream<NameSample> sampleStream = new NameSampleDataStream(lineStream)) {
      model = NameFinderME.train("eng", null, sampleStream, params,
          nameFinderFactory);
    }

    File modelFile = Files.createTempFile("model", ".bin").toFile();
    try (OutputStream modelOut =
             new BufferedOutputStream(new FileOutputStream(modelFile))) {
      model.serialize(modelOut);
    }
    return modelFile;
  }

}
