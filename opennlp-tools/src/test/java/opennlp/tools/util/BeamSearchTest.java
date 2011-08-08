/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

package opennlp.tools.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import java.util.HashMap;
import java.util.Map;

import opennlp.model.MaxentModel;

import org.junit.Test;

public class BeamSearchTest {

  static class IdentityFeatureGenerator implements BeamSearchContextGenerator<String> {
    
    private String[] outcomeSequence;
    
    IdentityFeatureGenerator(String outcomeSequence[]) {
      this.outcomeSequence = outcomeSequence;
    }
    
    public String[] getContext(int index, String[] sequence,
        String[] priorDecisions, Object[] additionalContext) {
      return new String[] {outcomeSequence[index]};
    }
  }
  
  
  static class IdentityModel implements MaxentModel {

    private String[] outcomes;
    
    private Map<String, Integer> outcomeIndexMap = new HashMap<String, Integer>();
    
    private double bestOutcomeProb = 0.8d;
    private double otherOutcomeProb;
    
    IdentityModel(String outcomes[]) {
      this.outcomes = outcomes;
      
      for (int i = 0; i < outcomes.length; i++) {
        outcomeIndexMap.put(outcomes[i], i);
      }
      
      otherOutcomeProb = 0.2d / (outcomes.length - 1);
    }
    
    public double[] eval(String[] context) {
      
      double probs[] = new double[outcomes.length];
      
      for (int i = 0; i < probs.length; i++) {
        if (outcomes[i].equals(context[0])) {
          probs[i] = bestOutcomeProb;
        }
        else {
          probs[i] = otherOutcomeProb;
        }
      }
      
      return probs;
    }

    public double[] eval(String[] context, double[] probs) {
      return eval(context);
    }

    public double[] eval(String[] context, float[] values) {
      return eval(context);
    }

    public String getAllOutcomes(double[] outcomes) {
      return null;
    }

    public String getBestOutcome(double[] outcomes) {
      return null;
    }

    public Object[] getDataStructures() {
      return null;
    }

    public int getIndex(String outcome) {
      return 0;
    }

    public int getNumOutcomes() {
      return outcomes.length;
    }

    public String getOutcome(int i) {
      return outcomes[i];
    }
  }
  
  /**
   * Tests that beam search does not fail to detect an empty sequence.
   */
  @Test
  public void testBestSequenceZeroLengthInput() {
    
    String sequence[] = new String[0];
    BeamSearchContextGenerator<String> cg = new IdentityFeatureGenerator(sequence);
    
    String outcomes[] = new String[] {"1", "2", "3"};
    MaxentModel model = new IdentityModel(outcomes);
    
    BeamSearch<String> bs = new BeamSearch<String>(3, cg, model);
    
    Sequence seq = bs.bestSequence(sequence, null);
    assertNotNull(seq);
    assertEquals(sequence.length, seq.getOutcomes().size());
  }
  
  /**
   * Tests finding a sequence of length one.
   */
  @Test
  public void testBestSequenceOneElementInput() {
    String sequence[] = {"1"};
    BeamSearchContextGenerator<String> cg = new IdentityFeatureGenerator(sequence);
    
    String outcomes[] = new String[] {"1", "2", "3"};
    MaxentModel model = new IdentityModel(outcomes);
    
    BeamSearch<String> bs = new BeamSearch<String>(3, cg, model);
    
    Sequence seq = bs.bestSequence(sequence, null);
    assertNotNull(seq);
    assertEquals(sequence.length, seq.getOutcomes().size());
    assertEquals("1", seq.getOutcomes().get(0));
  }
  
  /**
   * Tests finding the best sequence on a short input sequence.
   */
  @Test
  public void testBestSequence() {
    String sequence[] = {"1", "2", "3", "2", "1"};
    BeamSearchContextGenerator<String> cg = new IdentityFeatureGenerator(sequence);
    
    String outcomes[] = new String[] {"1", "2", "3"};
    MaxentModel model = new IdentityModel(outcomes);
    
    BeamSearch<String> bs = new BeamSearch<String>(2, cg, model);
    
    Sequence seq = bs.bestSequence(sequence, null);
    assertNotNull(seq);
    assertEquals(sequence.length, seq.getOutcomes().size());
    assertEquals("1", seq.getOutcomes().get(0));
    assertEquals("2", seq.getOutcomes().get(1));
    assertEquals("3", seq.getOutcomes().get(2));
    assertEquals("2", seq.getOutcomes().get(3));
    assertEquals("1", seq.getOutcomes().get(4));
  }
  
  /**
   * Tests finding the best sequence on a short input sequence.
   */
  @Test
  public void testBestSequenceWithValidator() {
    String sequence[] = {"1", "2", "3", "2", "1"};
    BeamSearchContextGenerator<String> cg = new IdentityFeatureGenerator(sequence);
    
    String outcomes[] = new String[] {"1", "2", "3"};
    MaxentModel model = new IdentityModel(outcomes);
    
    BeamSearch<String> bs = new BeamSearch<String>(2, cg, model, new SequenceValidator<String>(){

      public boolean validSequence(int i, String[] inputSequence,
          String[] outcomesSequence, String outcome) {
        return !"2".equals(outcome);
      }}, 0);
    
    Sequence seq = bs.bestSequence(sequence, null);
    assertNotNull(seq);
    assertEquals(sequence.length, seq.getOutcomes().size());
    assertEquals("1", seq.getOutcomes().get(0));
    assertNotSame("2", seq.getOutcomes().get(1));
    assertEquals("3", seq.getOutcomes().get(2));
    assertNotSame("2", seq.getOutcomes().get(3));
    assertEquals("1", seq.getOutcomes().get(4));
  }
}
