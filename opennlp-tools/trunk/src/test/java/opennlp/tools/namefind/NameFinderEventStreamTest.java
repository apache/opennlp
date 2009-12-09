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

package opennlp.tools.namefind;

import junit.framework.Assert;
import junit.framework.TestCase;
import opennlp.model.EventStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Span;

/**
 * This is the test class for {@link NameFinderEventStream}.
 */
public class NameFinderEventStreamTest extends TestCase {

  /**
   * Tests the correctly generated outcomes for a test sentence.
   */
  public void testOutcomesForSingleTypeSentence() {
    String sentence[] = {"Elise",
        "Wendel",
        "appreciated",
        "the",
        "hint",
        "and",
        "enjoyed",
        "a",
        "delicious",
        "traditional",
        "meal",
        "."};
    
    NameSample nameSample = new NameSample(sentence, 
        new Span[]{new Span(0, 2, "person")}, false);
    
    EventStream eventStream = new NameFinderEventStream(
        ObjectStreamUtils.createObjectStream(nameSample));
    
    Assert.assertTrue(eventStream.hasNext());
    Assert.assertEquals("person-" + NameFinderME.START, eventStream.next().getOutcome());
    Assert.assertTrue(eventStream.hasNext());
    Assert.assertEquals("person-" + NameFinderME.CONTINUE, eventStream.next().getOutcome());
    
    for (int i = 0; i < 10; i++) {
      Assert.assertTrue(eventStream.hasNext());
      Assert.assertEquals(NameFinderME.OTHER, eventStream.next().getOutcome());
    }
    
    Assert.assertFalse(eventStream.hasNext());
  }
}
