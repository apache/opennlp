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

package opennlp.model;

import java.util.StringTokenizer;


public abstract class AbstractModelReader {

  /**
   * The number of predicates contained in the model.
   */
  protected int NUM_PREDS;

  public AbstractModelReader() {
    super();
  }

  /**
   * Implement as needed for the format the model is stored in.
   */
  public abstract int readInt() throws java.io.IOException;

  /**
   * Implement as needed for the format the model is stored in.
   */
  public abstract double readDouble() throws java.io.IOException;

  /**
   * Implement as needed for the format the model is stored in.
   */
  public abstract String readUTF() throws java.io.IOException;
      
  public abstract AbstractModel getModel () throws java.io.IOException;

  public abstract void checkModelType() throws java.io.IOException;

  protected String[] getOutcomes() throws java.io.IOException {
      int numOutcomes = readInt();
      String[] outcomeLabels = new String[numOutcomes];
      for (int i=0; i<numOutcomes; i++) outcomeLabels[i] = readUTF();
      return outcomeLabels;
  }

  protected int[][] getOutcomePatterns() throws java.io.IOException {
      int numOCTypes = readInt();
      int[][] outcomePatterns = new int[numOCTypes][];
      for (int i=0; i<numOCTypes; i++) {
          StringTokenizer tok = new StringTokenizer(readUTF(), " ");
          int[] infoInts = new int[tok.countTokens()];
          for (int j = 0; tok.hasMoreTokens(); j++) {
              infoInts[j] = Integer.parseInt(tok.nextToken());
          }
          outcomePatterns[i] = infoInts;
      }
      return outcomePatterns;
  }

  protected String[] getPredicates() throws java.io.IOException {
      NUM_PREDS = readInt();
      String[] predLabels = new String[NUM_PREDS];
      for (int i=0; i<NUM_PREDS; i++)
          predLabels[i] = readUTF();
      return predLabels;
  }

  /**
   * Reads the parameters from a file and populates an array of context objects.
   * @param outcomePatterns The outcomes patterns for the model.  The first index refers to which 
   * outcome pattern (a set of outcomes that occurs with a context) is being specified.  The
   * second index specifies the number of contexts which use this pattern at index 0, and the
   * index of each outcomes which make up this pattern in indicies 1-n.  
   * @return An array of context objects.
   * @throws java.io.IOException when the model file does not match the outcome patterns or can not be read.
   */
  protected Context[] getParameters(int[][] outcomePatterns) throws java.io.IOException {
    Context[] params = new Context[NUM_PREDS];
    int pid=0;
    for (int i=0; i<outcomePatterns.length; i++) {
      //construct outcome pattern
      int[] outcomePattern = new int[outcomePatterns[i].length-1];
      for (int k=1; k<outcomePatterns[i].length; k++) {
        outcomePattern[k-1] = outcomePatterns[i][k];
      }
      //System.err.println("outcomePattern "+i+" of "+outcomePatterns.length+" with "+outcomePatterns[i].length+" outcomes ");
      //populate parameters for each context which uses this outcome pattern. 
      for (int j=0; j<outcomePatterns[i][0]; j++) {
        double[] contextParameters = new double[outcomePatterns[i].length-1];
        for (int k=1; k<outcomePatterns[i].length; k++) {
          contextParameters[k-1] = readDouble();
        }
        params[pid] = new Context(outcomePattern,contextParameters);
        pid++;
      }
    }
    return params;
  }

}
