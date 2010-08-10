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


package opennlp.tools.postag;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import junit.framework.TestCase;
import opennlp.tools.util.model.ModelType;

/**
 * Tests for the {@link POSTaggerME} class.
 */
public class POSTaggerMETest extends TestCase {

  /**
   * Trains a POSModel from the annotated test data.
   *
   * @return
   * @throws IOException
   */
  // TODO: also use tag dictionary for training
  static POSModel trainPOSModel() throws IOException {
    InputStream in = POSTaggerMETest.class.getClassLoader().getResourceAsStream(
        "opennlp/tools/postag/AnnotatedSentences.txt");

    return POSTaggerME.train("en", new WordTagSampleStream((
        new InputStreamReader(in))), ModelType.MAXENT, null, null, 5, 100);
  }

  public void testPOSTagger() throws IOException {
    POSModel posModel = trainPOSModel();

    POSTagger tagger = new POSTaggerME(posModel);

    String tags[] = tagger.tag(new String[] {
        "The",
    	"driver",
    	"got",
    	"badly",
    	"injured",
    	"."});

    assertEquals(6, tags.length);

    assertEquals("DT", tags[0]);
    assertEquals("NN", tags[1]);
    assertEquals("VBD", tags[2]);
    assertEquals("RB", tags[3]);
    assertEquals("VBN", tags[4]);
    assertEquals(".", tags[5]);
  }
}