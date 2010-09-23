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

package opennlp.tools.chunker;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ChunkSampleTest {

  @Test(expected=IllegalArgumentException.class)
  public void testParameterValidation() {
    new ChunkSample(new String[]{""}, new String[]{""},
        new String[]{"test", "one element to much"});
  }
  
  private String[] createSentence() {
    return new String[] {
        "Forecasts",
        "for",
        "the",
        "trade",
        "figures",
        "range",
        "widely",
        "."
    };
  }
  
  private String[] createTags() {
    
    return new String[]{
        "NNS",
        "IN",
        "DT",
        "NN",
        "NNS",
        "VBP",
        "RB",
        "."
    };
  }
  
  private String[] createChunks() {
    return new String[]{
        "B-NP",
        "B-PP",
        "B-NP",
        "I-NP",
        "I-NP",
        "B-VP",
        "B-ADVP"
        ,"O"
    };
  }
  
  @Test
  public void testRetrievingContent() {
    ChunkSample sample = new ChunkSample(createSentence(), createTags(), createChunks());
    
    assertArrayEquals(createSentence(), sample.getSentence());
    assertArrayEquals(createTags(), sample.getTags());
    assertArrayEquals(createChunks(), sample.getPreds());
  }
  
  @Test
  public void testToString() {
    
    ChunkSample sample = new ChunkSample(createSentence(), createTags(), createChunks());
    
    assertEquals(" [NP Forecasts_NNS ] [PP for_IN ] [NP the_DT trade_NN figures_NNS ] " +
    		"[VP range_VBP ] [ADVP widely_RB ] ._.", sample.toString());
  }
}
