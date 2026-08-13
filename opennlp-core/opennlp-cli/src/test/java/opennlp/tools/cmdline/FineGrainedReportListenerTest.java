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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.cmdline.postag.POSTaggerFineGrainedReportListener;
import opennlp.tools.postag.POSSample;

/**
 * Tests that the figures written by a {@link FineGrainedReportListener} do not change with
 * the JVM's default {@link Locale}.
 */
public class FineGrainedReportListenerTest {

  private final Locale defaultLocale = Locale.getDefault();

  @AfterEach
  void restoreDefaultLocale() {
    Locale.setDefault(defaultLocale);
  }

  @Test
  void testReportIsIndependentOfDefaultLocale() throws Exception {
    Locale.setDefault(Locale.GERMANY);

    final String report = createReport();

    // Two out of three tags are correct. A locale with a comma decimal separator would
    // render this as "66,67%" and change every figure in the report along with it.
    Assertions.assertTrue(report.contains("66.67%"),
        () -> "Report should contain the Locale.ROOT rendering of the accuracy:\n" + report);
    Assertions.assertFalse(report.contains("66,67%"),
        () -> "Report should not render figures with the default locale:\n" + report);
  }

  private static String createReport() throws Exception {
    final String[] sentence = {"He", "runs", "fast"};
    final POSSample reference = new POSSample(sentence, new String[] {"PRP", "VBZ", "RB"});
    final POSSample prediction = new POSSample(sentence, new String[] {"PRP", "VBZ", "JJ"});

    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      final POSTaggerFineGrainedReportListener listener =
          new POSTaggerFineGrainedReportListener(out);
      listener.misclassified(reference, prediction);
      listener.writeReport();
      return out.toString(StandardCharsets.UTF_8);
    }
  }
}
