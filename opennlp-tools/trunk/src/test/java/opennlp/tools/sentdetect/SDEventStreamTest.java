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

package opennlp.tools.sentdetect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import opennlp.model.EventStream;
import opennlp.tools.sentdetect.lang.Factory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Span;

import org.junit.Test;

/**
 * Tests for the {@link SDEventStream} class.
 */
public class SDEventStreamTest {
  
  @Test
  public void testEventOutcomes() throws IOException {
    // Sample with two sentences
    SentenceSample sample = new SentenceSample("Test sent. one. Test sent. 2?", 
        new Span(0, 15), new Span(16, 29));
    
    ObjectStream<SentenceSample> sampleStream = 
      ObjectStreamUtils.createObjectStream(sample);
    
    Factory factory = new Factory();
    
    EventStream eventStream = new SDEventStream(sampleStream,
        factory.createSentenceContextGenerator("en"),
        factory.createEndOfSentenceScanner("en"));
    
    assertTrue(eventStream.hasNext());
    assertEquals(SentenceDetectorME.NO_SPLIT, eventStream.next().getOutcome());
    
    assertTrue(eventStream.hasNext());
    assertEquals(SentenceDetectorME.SPLIT, eventStream.next().getOutcome());

    assertTrue(eventStream.hasNext());
    assertEquals(SentenceDetectorME.NO_SPLIT, eventStream.next().getOutcome());

    assertTrue(eventStream.hasNext());
    assertEquals(SentenceDetectorME.SPLIT, eventStream.next().getOutcome());
    
    assertFalse(eventStream.hasNext());
  }
}
