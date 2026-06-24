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
package opennlp.tools.util.normalizer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfusablesLoadTest {

  private static InputStream in(String data) {
    return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void parseReadsWellFormedMappings() throws Exception {
    final Map<Integer, String> map = Confusables.parse(in("0041 ;\t0042 ;\tMA\t# comment\n"));
    assertEquals("B", map.get(0x41)); // A -> prototype B
  }

  @Test
  void parseFailsLoudOnStructurallyMalformedLine() {
    // A non-comment data line with fewer than two ';' is structurally malformed. It must fail loud
    // (like the malformed-hex-token path and the sibling loaders) rather than being silently dropped,
    // which would yield a quietly-incomplete confusable map and wrong confusable() results.
    final String data = "0041 ;\t0042 ;\tMA\n"     // line 1: valid
        + "0043 0044\n";                            // line 2: no ';' -> malformed
    final IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> Confusables.parse(in(data)));
    assertTrue(ex.getMessage().contains("line 2"), ex.getMessage());
  }
}
