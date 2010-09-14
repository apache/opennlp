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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ChunkSampleTest {

  @Test(expected=IllegalArgumentException.class)
  public void testParameterValidation() {
    new ChunkSample(new String[]{""}, new String[]{""},
        new String[]{"test", "one element to much"});
  }
  
  @Test
  public void testToString() {
    
    String sentence[] = new String[] {
        "Forecasts",
        "for",
        "the",
        "trade",
        "figures",
        "range",
        "widely",
        "."
    };
    
    String tags[] = new String[]{
        "NNS",
        "IN",
        "DT",
        "NN",
        "NNS",
        "VBP",
        "RB",
        "."
    };
    
    String chunks[] = new String[]{
        "B-NP",
        "B-PP",
        "B-NP",
        "I-NP",
        "I-NP",
        "B-VP",
        "B-ADVP"
        ,"O"
    };
    
    ChunkSample sample = new ChunkSample(sentence, tags, chunks);
    
    assertEquals(" [NP Forecasts_NNS ] [PP for_IN ] [NP the_DT trade_NN figures_NNS ] " +
    		"[VP range_VBP ] [ADVP widely_RB ] ._.", sample.toString());
    
  }
}
