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
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HeadRulesTest {

  @Test
  void testSerialization() throws IOException {
    InputStream headRulesIn =
        HeadRulesTest.class.getResourceAsStream("/opennlp/tools/parser/en_head_rules");

    HeadRules headRulesOrginal = new HeadRules(new InputStreamReader(headRulesIn, StandardCharsets.UTF_8));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    headRulesOrginal.serialize(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    out.close();

    HeadRules headRulesRecreated = new HeadRules(new InputStreamReader(
        new ByteArrayInputStream(out.toByteArray()), StandardCharsets.UTF_8));

    Assertions.assertEquals(headRulesOrginal, headRulesRecreated);
  }
}
