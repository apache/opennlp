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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import opennlp.tools.depparse.DependencyTree;

/**
 * The CoNLL-U data, expected output, and output and log capture shared by the dependency
 * parser tool tests.
 */
final class DependencyToolTestSupport {

  private DependencyToolTestSupport() {
  }

  /** A two-token sentence in CoNLL-U; the tests repeat it to build a corpus. */
  static final String SENTENCE = """
      1\tdogs\tdog\tNOUN\tNNS\t_\t2\tnsubj\t_\t_
      2\trun\trun\tVERB\tVBP\t_\t0\troot\t_\t_

      """;

  /** How often {@link #SENTENCE} is repeated in the training data. */
  static final int REPETITIONS = 20;

  /** The gold tree of {@link #SENTENCE}. */
  static final DependencyTree GOLD =
      DependencyTree.of(new int[] {1, -1}, new String[] {"nsubj", "root"});

  /** The sentence with the heads swapped, which a parser trained on {@link #SENTENCE} misparses. */
  static final String REVERSED_SENTENCE = """
      1\tdogs\tdog\tNOUN\tNNS\t_\t0\troot\t_\t_
      2\trun\trun\tVERB\tVBP\t_\t1\tobj\t_\t_

      """;

  /** The expected block the error listener prints for {@link #REVERSED_SENTENCE}. */
  static final String EXPECTED_REVERSED = "Expected: {\n"
      + "1\tdogs\tNOUN\t0\troot" + System.lineSeparator()
      + "2\trun\tVERB\t1\tobj" + System.lineSeparator() + "}";

  /** The predicted block the error listener prints for the parse of {@link #SENTENCE}. */
  static final String PREDICTED_SENTENCE = "Predicted: {\n"
      + "1\tdogs\tNOUN\t2\tnsubj" + System.lineSeparator()
      + "2\trun\tVERB\t0\troot" + System.lineSeparator() + "}";

  /** The CoNLL-U output of the parser tools for {@code dogs_NOUN run_VERB}. */
  static final String PARSED_SENTENCE = "1\tdogs\t_\t_\tNOUN\t_\t2\tnsubj\t_\t_\n"
      + "2\trun\t_\t_\tVERB\t_\t0\troot\t_\t_\n\n";

  /** The scores logged for a parser that reproduces all 40 tokens of the data. */
  static final String PERFECT_SCORES = "Tokens: 40; UAS: 1.0; LAS: 1.0";

  /** The punctuation-free scores logged for the same parser; the data has no punctuation. */
  static final String PERFECT_SCORES_EXCLUDING_PUNCTUATION =
      "Tokens excluding punctuation: 40; UAS: 1.0; LAS: 1.0";

  /** The message of the CoNLL-U format for an encoding other than UTF-8. */
  static final String UTF_8_REQUIRED = "CoNLL-U data must use UTF-8";

  /**
   * Runs a tool while capturing the INFO messages its logger emits.
   *
   * @param tool The tool class whose logger is captured.
   * @param run Runs the tool.
   * @return The formatted log messages, in order. Never {@code null}.
   */
  static List<String> logOf(Class<?> tool, Runnable run) {
    final Logger logger = (Logger) LoggerFactory.getLogger(tool);
    final Level previous = logger.getLevel();
    final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.INFO);
    try {
      run.run();
    } finally {
      logger.setLevel(previous);
      logger.detachAppender(appender);
    }
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  /**
   * Runs a tool with the given standard input while capturing its standard output.
   *
   * @param input The standard input, encoded as UTF-8.
   * @param run Runs the tool.
   * @return The standard output, with line breaks normalized to {@code \n}. Never
   *         {@code null}.
   */
  static String outputOf(String input, Runnable run) {
    final InputStream previousIn = System.in;
    final PrintStream previousOut = System.out;
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
      System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
      run.run();
    } finally {
      System.setIn(previousIn);
      System.setOut(previousOut);
    }
    return output.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
  }
}
