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

package opennlp.tools.cmdline.stopword;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StopwordFilterToolTest {

  private InputStream originalIn;
  private PrintStream originalOut;
  private ByteArrayOutputStream capturedOut;

  @BeforeEach
  void redirectStreams() {
    originalIn = System.in;
    originalOut = System.out;
    capturedOut = new ByteArrayOutputStream();
    System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restoreStreams() {
    System.setIn(originalIn);
    System.setOut(originalOut);
  }

  @Test
  void filtersEnglishStopwordsFromStdin() {
    final String in = "the quick brown fox\n";
    System.setIn(new ByteArrayInputStream(in.getBytes(StandardCharsets.UTF_8)));

    StopwordFilterTool tool = new StopwordFilterTool();
    tool.run(new String[] {"en"});

    String out = capturedOut.toString(StandardCharsets.UTF_8);
    int idxQuick = out.indexOf("quick");
    int idxBrown = out.indexOf("brown");
    int idxFox = out.indexOf("fox");

    Assertions.assertTrue(idxQuick >= 0, "Expected 'quick' in output, was: " + out);
    Assertions.assertTrue(idxBrown > idxQuick, "Expected 'brown' after 'quick' in output, was: " + out);
    Assertions.assertTrue(idxFox > idxBrown, "Expected 'fox' after 'brown' in output, was: " + out);
    Assertions.assertFalse(out.contains("the"),
        "Did not expect 'the' to appear in output, was: " + out);
  }

  @Test
  void printsHelpWhenNoArgs() {
    // empty stdin in case run() reads it
    System.setIn(new ByteArrayInputStream(new byte[0]));

    StopwordFilterTool tool = new StopwordFilterTool();
    tool.run(new String[] {});

    String out = capturedOut.toString(StandardCharsets.UTF_8);
    Assertions.assertTrue(out.contains("Usage") || out.contains("lang"),
        "Expected help message containing 'Usage' or 'lang', was: " + out);
  }

  @Test
  void unsupportedLanguageThrows() {
    System.setIn(new ByteArrayInputStream(new byte[0]));

    StopwordFilterTool tool = new StopwordFilterTool();
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> tool.run(new String[] {"xx"}));
  }
}
