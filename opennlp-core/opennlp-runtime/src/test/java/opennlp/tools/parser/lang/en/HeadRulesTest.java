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

package opennlp.tools.parser.lang.en;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HeadRulesTest {

  /**
   * Positive: a well-formed head rules line with a small tag count loads without error.
   */
  @Test
  void testValidTagCountLoads() throws IOException {
    // "5 NP 1 NN NNS" — num=5, tags=3 (5-2=3)
    String rules = "5 NP 1 NN NNS NNP\n";
    Assertions.assertDoesNotThrow(() -> new HeadRules(new StringReader(rules)));
  }

  /**
   * Negative: a head rules line with a huge tag count must throw IOException,
   * not attempt to allocate Integer.MAX_VALUE bytes.
   */
  @Test
  void testOversizedTagCountThrows() {
    String rules = "2147483647 NP 1\n";
    Assertions.assertThrows(IOException.class,
        () -> new HeadRules(new StringReader(rules)));
  }

  /**
   * Negative: a tag count that would produce a negative array size must throw IOException.
   */
  @Test
  void testNegativeTagCountThrows() {
    String rules = "1 NP 1\n";  // 1 - 2 = -1
    Assertions.assertThrows(IOException.class,
        () -> new HeadRules(new StringReader(rules)));
  }

  /**
   * Boundary: value just above MAX_TAGS_PER_RULE (1003 → numTags = 1001) must throw IOException.
   */
  @Test
  void testJustAboveLimitThrows() {
    // 1003 declared; 1003 - 2 = 1001 tags, which exceeds MAX_TAGS_PER_RULE (1000)
    String rules = "1003 NP 1\n";
    Assertions.assertThrows(IOException.class,
        () -> new HeadRules(new StringReader(rules)));
  }

  /**
   * Negative: non-numeric token count must throw IOException, not NumberFormatException.
   */
  @Test
  void testNonNumericTagCountThrows() {
    String rules = "NaN NP 1\n";
    Assertions.assertThrows(IOException.class,
        () -> new HeadRules(new StringReader(rules)));
  }

  @Test
  void testSerialization() throws IOException {
    try (InputStream headRulesIn =
        HeadRulesTest.class.getResourceAsStream("/opennlp/tools/parser/en_head_rules");
        InputStreamReader reader = new InputStreamReader(headRulesIn, StandardCharsets.UTF_8)) {

      HeadRules headRulesOrginal = new HeadRules(reader);

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      headRulesOrginal.serialize(new OutputStreamWriter(out, StandardCharsets.UTF_8));
      out.close();

      HeadRules headRulesRecreated = new HeadRules(new InputStreamReader(
              new ByteArrayInputStream(out.toByteArray()), StandardCharsets.UTF_8));

      Assertions.assertEquals(headRulesOrginal, headRulesRecreated);
    }
  }
}
