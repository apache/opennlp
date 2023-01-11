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

package opennlp.tools.ml.model;

import java.io.IOException;
import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

public class OnePassRealValueDataIndexerTest {

  DataIndexer indexer;

  @BeforeEach
  void setUp()  {
    indexer = new OnePassRealValueDataIndexer();
    indexer.init(new TrainingParameters(Collections.emptyMap()), null);
  }

  @Test
  void testIndex() throws IOException {
    // He belongs to <START:org> Apache Software Foundation <END> .
    ObjectStream<Event> eventStream = new SimpleEventStreamBuilder()
        .add("other/w=he n1w=belongs n2w=to po=other pow=other,He powf=other,ic ppo=other")
        .add("other/w=belongs p1w=he n1w=to n2w=apache po=other pow=other,belongs powf=other,lc ppo=other")
        .add("other/w=to p1w=belongs p2w=he n1w=apache n2w=software po=other pow=other,to" +
            " powf=other,lc ppo=other")
        .add("org-start/w=apache p1w=to p2w=belongs n1w=software n2w=foundation po=other pow=other,Apache" +
            " powf=other,ic ppo=other")
        .add("org-cont/w=software p1w=apache p2w=to n1w=foundation n2w=. po=org-start" +
            " pow=org-start,Software powf=org-start,ic ppo=other")
        .add("org-cont/w=foundation p1w=software p2w=apache n1w=. po=org-cont pow=org-cont,Foundation" +
            " powf=org-cont,ic ppo=org-start")
        .add("other/w=. p1w=foundation p2w=software po=org-cont pow=org-cont,. powf=org-cont,other" +
            " ppo=org-cont")
        .build();

    indexer.index(eventStream);
    Assertions.assertEquals(3, indexer.getContexts().length);
    Assertions.assertArrayEquals(new int[] {0}, indexer.getContexts()[0]);
    Assertions.assertArrayEquals(new int[] {0}, indexer.getContexts()[1]);
    Assertions.assertArrayEquals(new int[] {0}, indexer.getContexts()[2]);
    Assertions.assertEquals(3, indexer.getValues().length);
    Assertions.assertNull(indexer.getValues()[0]);
    Assertions.assertNull(indexer.getValues()[1]);
    Assertions.assertNull(indexer.getValues()[2]);
    Assertions.assertEquals(5, indexer.getNumEvents());
    Assertions.assertArrayEquals(new int[] {0, 1, 2}, indexer.getOutcomeList());
    Assertions.assertArrayEquals(new int[] {3, 1, 1}, indexer.getNumTimesEventsSeen());
    Assertions.assertArrayEquals(new String[] {"ppo=other"}, indexer.getPredLabels());
    Assertions.assertArrayEquals(new String[] {"other", "org-start", "org-cont"}, indexer.getOutcomeLabels());
    Assertions.assertArrayEquals(new int[] {5}, indexer.getPredCounts());
  }

  @Test
  void testIndexValues() throws IOException {
    // He belongs to <START:org> Apache Software Foundation <END> .
    ObjectStream<Event> eventStream = new SimpleEventStreamBuilder()
        .add("other/w=he;0.1 n1w=belongs;0.2 n2w=to;0.1 po=other;0.1" +
            " pow=other,He;0.1 powf=other,ic;0.1 ppo=other;0.1")
        .add("other/w=belongs;0.1 p1w=he;0.2 n1w=to;0.1 n2w=apache;0.1" +
            " po=other;0.1 pow=other,belongs;0.1 powf=other,lc;0.1 ppo=other;0.1")
        .add("other/w=to;0.1 p1w=belongs;0.2 p2w=he;0.1 n1w=apache;0.1" +
            " n2w=software;0.1 po=other;0.1 pow=other,to;0.1 powf=other,lc;0.1 ppo=other;0.1")
        .add("org-start/w=apache;0.1 p1w=to;0.2 p2w=belongs;0.1 n1w=software;0.1 n2w=foundation;0.1" +
            " po=other;0.1 pow=other,Apache;0.1 powf=other,ic;0.1 ppo=other;0.1")
        .add("org-cont/w=software;0.1 p1w=apache;0.2 p2w=to;0.1 n1w=foundation;0.1" +
            " n2w=.;0.1 po=org-start;0.1 pow=org-start,Software;0.1 powf=org-start,ic;0.1 ppo=other;0.1")
        .add("org-cont/w=foundation;0.1 p1w=software;0.2 p2w=apache;0.1 n1w=.;0.1 po=org-cont;0.1" +
            " pow=org-cont,Foundation;0.1 powf=org-cont,ic;0.1 ppo=org-start;0.1")
        .add("other/w=.;0.1 p1w=foundation;0.1 p2w=software;0.1 po=org-cont;0.1 pow=org-cont,.;0.1" +
            " powf=org-cont,other;0.1 ppo=org-cont;0.1")
        .build();

    indexer.index(eventStream);
    Assertions.assertEquals(3, indexer.getContexts().length);
    Assertions.assertArrayEquals(new int[] {0}, indexer.getContexts()[0]);
    Assertions.assertArrayEquals(new int[] {0}, indexer.getContexts()[1]);
    Assertions.assertArrayEquals(new int[] {0}, indexer.getContexts()[2]);
    Assertions.assertEquals(3, indexer.getValues().length);
    final float delta = 0.001F;
    Assertions.assertArrayEquals(
        indexer.getValues()[0], new float[] {0.1F, 0.2F, 0.1F, 0.1F, 0.1F, 0.1F, 0.1F}, delta);
    Assertions.assertArrayEquals(
        indexer.getValues()[1], new float[] {0.1F, 0.2F, 0.1F, 0.1F, 0.1F, 0.1F, 0.1F, 0.1F, 0.1F}, delta);
    Assertions.assertArrayEquals(
        indexer.getValues()[2], new float[] {0.1F, 0.2F, 0.1F, 0.1F, 0.1F, 0.1F, 0.1F, 0.1F, 0.1F}, delta);
    Assertions.assertEquals(5, indexer.getNumEvents());
    Assertions.assertArrayEquals(new int[] {0, 1, 2}, indexer.getOutcomeList());
    Assertions.assertArrayEquals(new int[] {3, 1, 1}, indexer.getNumTimesEventsSeen());
    Assertions.assertArrayEquals(new String[] {"ppo=other"}, indexer.getPredLabels());
    Assertions.assertArrayEquals(new String[] {"other", "org-start", "org-cont"}, indexer.getOutcomeLabels());
    Assertions.assertArrayEquals(new int[] {5}, indexer.getPredCounts());
  }
}
