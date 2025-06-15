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

package opennlp.tools.util;

import java.io.IOException;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import opennlp.tools.EnabledWhenCDNAvailable;
import opennlp.tools.models.ModelType;
import opennlp.tools.sentdetect.SentenceModel;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledWhenCDNAvailable(hostname = "dlcdn.apache.org")
public class DownloadUtilDownloadTwiceTest {

  /*
   * Programmatic change to debug log to ensure that we can see log messages to
   * confirm no duplicate download is happening
   */
  @BeforeAll
  public static void prepare() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger logger = context.getLogger("opennlp");
    logger.setLevel(Level.DEBUG);
  }

  /*
   * Programmatic restore the default log level (= OFF) after the test
   */
  @AfterAll
  public static void cleanup() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger logger = context.getLogger("opennlp");
    logger.setLevel(Level.OFF);
  }

  @Test
  public void testDownloadModelTwice() throws IOException {
    String lang = "de";
    ModelType type = ModelType.SENTENCE_DETECTOR;
    
    try (LogCaptor logCaptor = LogCaptor.forClass(DownloadUtil.class)) {
      boolean alreadyDownloaded = DownloadUtil.existsModel(lang, type);
      DownloadUtil.downloadModel(lang, type, SentenceModel.class);

      if (! alreadyDownloaded) {
        assertEquals(2, logCaptor.getDebugLogs().size());
        checkDebugLogsContainMessageFragment(logCaptor.getDebugLogs(), "Download complete.");
      } else {
        assertEquals(1, logCaptor.getDebugLogs().size());
        checkDebugLogsContainMessageFragment(logCaptor.getDebugLogs(), "already exists. Skipping download.");
      }
      logCaptor.clearLogs();

      // try to download again
      DownloadUtil.downloadModel(lang, type, SentenceModel.class);
      assertEquals(1, logCaptor.getDebugLogs().size());
      checkDebugLogsContainMessageFragment(logCaptor.getDebugLogs(), "already exists. Skipping download.");
      logCaptor.clearLogs();

    }
  }

  private void checkDebugLogsContainMessageFragment(List<String> debugLogs, String message) {
    for (String log : debugLogs) {
      if (log.contains(message)) {
        return;
      }
    }
    throw new AssertionError("Expected message fragment not found in logs: " + message);
  }

}
