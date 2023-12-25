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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import opennlp.tools.AbstractTempDirTest;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.InvalidFormatException;

/**
 * Tests for the {@link TokenizerTrainerTool} class.
 */
public class TokenizerTrainerToolTest extends AbstractTempDirTest {

  private TokenizerTrainerTool tokenizerTrainerTool;

  private final String sampleSuccessData =
      "Pierre Vinken<SPLIT>, 61 years old<SPLIT>, will join the board as a nonexecutive " +
          "director Nov. 29<SPLIT>.\n" +
          "Mr. Vinken is chairman of Elsevier N.V.<SPLIT>, the Dutch publishing group<SPLIT>.\n" +
          "Rudolph Agnew<SPLIT>, 55 years old and former chairman of Consolidated Gold Fields PLC<SPLIT>,\n" +
          "    was named a nonexecutive director of this British industrial conglomerate<SPLIT>.\n";

  private final String sampleFailureData = "It is Fail Test Case.\n\nNothing in this sentence.";

  @Test
  public void testGetShortDescription() {
    tokenizerTrainerTool = new TokenizerTrainerTool();
    Assertions.assertEquals(tokenizerTrainerTool.getShortDescription() ,
        "Trainer for the learnable tokenizer");
  }

  @Test
  public void testLoadDictHappyCase() throws IOException {
    File dictFile = new File("lang/ga/abb_GA.xml");
    Dictionary dict = TokenizerTrainerTool.loadDict(dictFile);
    Assertions.assertNotNull(dict);
  }

  @Test
  public void testLoadDictFailCase() {
    Assertions.assertThrows(InvalidFormatException.class , () ->
            TokenizerTrainerTool.loadDict(prepareDataFile("")));
  }

  //TODO OPENNLP-1447
  @Disabled(value = "OPENNLP-1447: These kind of tests won't work anymore. " +
          "We need to find a way to redirect log output (i.e. implement " +
          "a custom log adapter and plug it in, if we want to do such tests.")
  public void testTestRunHappyCase() throws IOException {
    File model = tempDir.resolve("model-en.bin").toFile();

    String[] args =
        new String[] { "-model" , model.getAbsolutePath() , "-alphaNumOpt" , "false" , "-lang" , "en" ,
            "-data" , String.valueOf(prepareDataFile(sampleSuccessData)) , "-encoding" , "UTF-8" };

    InputStream stream = new ByteArrayInputStream(sampleSuccessData.getBytes(StandardCharsets.UTF_8));
    System.setIn(stream);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos);
    System.setOut(ps);

    tokenizerTrainerTool = new TokenizerTrainerTool();
    tokenizerTrainerTool.run(StreamFactoryRegistry.DEFAULT_FORMAT , args);

    final String content = baos.toString(StandardCharsets.UTF_8);
    Assertions.assertTrue(content.contains("Number of Event Tokens: 171"));
    Assertions.assertTrue(model.delete());
  }

  //TODO OPENNLP-1447
  @Disabled(value = "OPENNLP-1447: These kind of tests won't work anymore. " +
          "We need to find a way to redirect log output (i.e. implement " +
          "a custom log adapter and plug it in, if we want to do such tests.")
  public void testTestRunExceptionCase() throws IOException {
    File model = tempDir.resolve("model-en.bin").toFile();
    model.deleteOnExit();

    String[] args =
        new String[] { "-model" , model.getAbsolutePath() , "-alphaNumOpt" , "false" , "-lang" , "en" ,
            "-data" , String.valueOf(prepareDataFile(sampleFailureData)) , "-encoding" , "UTF-8" };

    InputStream stream = new ByteArrayInputStream(sampleFailureData.getBytes(StandardCharsets.UTF_8));
    System.setIn(stream);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos);
    System.setOut(ps);

    Assertions.assertThrows(TerminateToolException.class , () -> {
      tokenizerTrainerTool = new TokenizerTrainerTool();
      tokenizerTrainerTool.run(StreamFactoryRegistry.DEFAULT_FORMAT , args);
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
