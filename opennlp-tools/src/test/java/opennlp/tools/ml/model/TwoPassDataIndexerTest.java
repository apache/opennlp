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

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

public class TwoPassDataIndexerTest {

  @Test
  public void testIndex() throws IOException {
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

    DataIndexer indexer = new TwoPassDataIndexer();
    indexer.init(new TrainingParameters(Collections.emptyMap()), null);
    indexer.index(eventStream);
    Assert.assertEquals(3, indexer.getContexts().length);
    Assert.assertArrayEquals(new int[]{0}, indexer.getContexts()[0]);
    Assert.assertArrayEquals(new int[]{0}, indexer.getContexts()[1]);
    Assert.assertArrayEquals(new int[]{0}, indexer.getContexts()[2]);
    Assert.assertNull(indexer.getValues());
    Assert.assertEquals(5, indexer.getNumEvents());
    Assert.assertArrayEquals(new int[]{0, 1, 2}, indexer.getOutcomeList());
    Assert.assertArrayEquals(new int[]{3, 1, 1}, indexer.getNumTimesEventsSeen());
    Assert.assertArrayEquals(new String[]{"ppo=other"}, indexer.getPredLabels());
    Assert.assertArrayEquals(new String[]{"other", "org-start", "org-cont"}, indexer.getOutcomeLabels());
    Assert.assertArrayEquals(new int[]{5}, indexer.getPredCounts());
  }
}
