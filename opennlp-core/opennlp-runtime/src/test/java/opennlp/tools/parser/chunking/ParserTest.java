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

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;

import opennlp.tools.parser.AbstractParserModelTest;
import opennlp.tools.parser.HeadRules;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.parser.ParserTestUtil;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Tests for the {@link opennlp.tools.parser.chunking.Parser} class.
 */
public class ParserTest extends AbstractParserModelTest {

  /* Trained dynamically before test */
  private static ParserModel model;

  @Override
  protected ParserModel getModel() {
    return model;
  }

  @BeforeAll
  public static void setupEnvironment() throws IOException {
    ObjectStream<Parse> parseSamples = ParserTestUtil.openTestTrainingData();
    HeadRules headRules = ParserTestUtil.createTestHeadRules();
    // Training an English lang 'opennlp.tools.parser.chunking.Parse'
    model = Parser.train("eng", parseSamples, headRules, TrainingParameters.defaultParams());
    Assertions.assertNotNull(model);
    Assertions.assertFalse(model.isLoadedFromSerialized());
  }

}
