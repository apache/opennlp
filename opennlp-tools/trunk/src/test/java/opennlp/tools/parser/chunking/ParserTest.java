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

package opennlp.tools.parser.chunking;

import org.junit.Test;

import opennlp.tools.parser.HeadRules;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParserTestUtil;
import opennlp.tools.util.ObjectStream;
import junit.framework.TestCase;

public class ParserTest extends TestCase {
  
  /**
   * Verify that training the parser does not fails cause
   * of any runtime problems.
   */
  @Test
  public void testParserTraining() throws Exception {
    
    ObjectStream<Parse> parseSamples = ParserTestUtil.openTestTrainingData();
    HeadRules headRules = ParserTestUtil.createTestHeadRules();
    
    Parser.train("en", parseSamples, headRules, 100, 0);
    
    // TODO: test if parser works kind of ...
  }
}
