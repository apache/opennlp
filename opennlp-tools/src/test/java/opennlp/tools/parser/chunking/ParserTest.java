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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.Test;

import opennlp.tools.parser.HeadRules;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParserFactory;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.parser.ParserTestUtil;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Tests for the {@link Parser} class.
 */
public class ParserTest {

  /**
   * Verify that training and tagging does not cause
   * runtime problems.
   */
  @Test
  public void testChunkingParserTraining() throws Exception {

    ObjectStream<Parse> parseSamples = ParserTestUtil.openTestTrainingData();
    HeadRules headRules = ParserTestUtil.createTestHeadRules();

    ParserModel model = Parser.train("en", parseSamples, headRules,
        TrainingParameters.defaultParams());

    opennlp.tools.parser.Parser parser = ParserFactory.create(model);

    // TODO:
    // Tests parsing to make sure the code does not has
    // a bug which fails always with a runtime exception
    // parser.parse(Parse.parseParse("She was just another freighter from the " +
    // "States and she seemed as commonplace as her name ."));

    // Test serializing and de-serializing model
    ByteArrayOutputStream outArray = new ByteArrayOutputStream();
    model.serialize(outArray);
    outArray.close();

    ParserModel outputModel =  new ParserModel(new ByteArrayInputStream(outArray.toByteArray()));

    // TODO: compare both models
  }
}
