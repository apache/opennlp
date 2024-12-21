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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import opennlp.tools.AbstractTempDirTest;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link TokenizerTrainerTool} class.
 */
public class TokenizerTrainerToolTest extends AbstractTempDirTest {

  private final String sampleSuccessData =
      "Pierre Vinken<SPLIT>, 61 years old<SPLIT>, will join the board as a nonexecutive " +
          "director Nov. 29<SPLIT>.\n" +
          "Mr. Vinken is chairman of Elsevier N.V.<SPLIT>, the Dutch publishing group<SPLIT>.\n" +
          "Rudolph Agnew<SPLIT>, 55 years old and former chairman of Consolidated Gold Fields PLC<SPLIT>,\n" +
          "    was named a nonexecutive director of this British industrial conglomerate<SPLIT>.\n";

  private final String sampleFailureData = "It is Fail Test Case.\n\nNothing in this sentence.";

  /*
   * Programmatic change to debug log to ensure that we can see log messages to
   * confirm no duplicate download is happening
   */
  @BeforeAll
  public static void prepare() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger logger = context.getLogger("opennlp.tools.cmdline.CmdLineUtil");
    logger.setLevel(Level.INFO);
  }

  /*
   * Programmatic restore the default log level (= OFF) after the test
   */
  @AfterAll
  public static void cleanup() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger logger = context.getLogger("opennlp.tools.cmdline.CmdLineUtil");
    logger.setLevel(Level.OFF);
  }

  @Test
  public void testGetShortDescription() {
    TokenizerTrainerTool tokenizerTrainerTool = new TokenizerTrainerTool();
    assertEquals("Trainer for the learnable tokenizer",
            tokenizerTrainerTool.getShortDescription());
  }

  @Test
  public void testLoadDictHappyCase() throws IOException {
    File dictFile = new File("lang/ga/abb_GA.xml");
    Dictionary dict = TokenizerTrainerTool.loadDict(dictFile);
    assertNotNull(dict);
  }

  @Test
  public void testLoadDictFailCase() {
    assertThrows(InvalidFormatException.class , () ->
            TokenizerTrainerTool.loadDict(prepareDataFile("")));
  }

  @Test
  public void testTestRunHappyCase() throws IOException {
    try (LogCaptor logCaptor = LogCaptor.forClass(CmdLineUtil.class)) {
      File model = tempDir.resolve("model-en.bin").toFile();
  
      String[] args =
          new String[] { "-model" , model.getAbsolutePath() , "-alphaNumOpt" , "false" , "-lang" , "en" ,
              "-data" , String.valueOf(prepareDataFile(sampleSuccessData)) , "-encoding" , "UTF-8" };
  
      InputStream stream = new ByteArrayInputStream(sampleSuccessData.getBytes(StandardCharsets.UTF_8));
      System.setIn(stream);
  
      TokenizerTrainerTool trainerTool = new TokenizerTrainerTool();
      trainerTool.run(StreamFactoryRegistry.DEFAULT_FORMAT , args);
  
      assertEquals(3, logCaptor.getInfoLogs().size());
      final String content = logCaptor.getInfoLogs().get(2);
      assertTrue(content.startsWith("Wrote tokenizer model to path:"));
      assertTrue(model.delete());
    }
  }

  @Test
  public void testTestRunExceptionCase() throws IOException {
    File model = tempDir.resolve("model-en.bin").toFile();
    model.deleteOnExit();

    String[] args =
        new String[] { "-model" , model.getAbsolutePath() , "-alphaNumOpt" , "false" , "-lang" , "en" ,
            "-data" , String.valueOf(prepareDataFile(sampleFailureData)) , "-encoding" , "UTF-8" };

    assertThrows(TerminateToolException.class , () -> {
      TokenizerTrainerTool trainerTool = new TokenizerTrainerTool();
      trainerTool.run(StreamFactoryRegistry.DEFAULT_FORMAT , args);
    });
  }

  // This is guaranteed to be deleted after the test finishes.
  private File prepareDataFile(String input) throws IOException {
    Path dataFile = tempDir.resolve("data-en.train");
    Files.writeString(dataFile, input, StandardCharsets.UTF_8, StandardOpenOption.CREATE);
    File f = dataFile.toFile();
    f.deleteOnExit();
    return f;
  }
}
