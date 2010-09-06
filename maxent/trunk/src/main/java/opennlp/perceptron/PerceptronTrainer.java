/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.perceptron;

import opennlp.model.AbstractModel;
import opennlp.model.DataIndexer;
import opennlp.model.EvalParameters;
import opennlp.model.MutableContext;

/**
 * Trains models using the perceptron algorithm.  Each outcome is represented as
 * a binary perceptron classifier.  This supports standard (integer) weighting as well
 * average weighting as described in:
 * Discriminative Training Methods for Hidden Markov Models: Theory and Experiments
 * with the Perceptron Algorithm. Michael Collins, EMNLP 2002.
 *
 */
public class PerceptronTrainer {

  /** Number of unique events which occurred in the event set. */
  private int numUniqueEvents;
  /** Number of events in the event set. */
  private int numEvents;
  
  /** Number of predicates. */
  private int numPreds; 
  /** Number of outcomes. */
  private int numOutcomes; 
  /** Records the array of predicates seen in each event. */
  private int[][] contexts;

  /** The value associates with each context. If null then context values are assumes to be 1. */
  private float[][] values;

  /** List of outcomes for each event i, in context[i]. */
  private int[] outcomeList;

  /** Records the num of times an event has been seen for each event i, in context[i]. */
  private int[] numTimesEventsSeen;
  
  /** Stores the String names of the outcomes.  The GIS only tracks outcomes
  as ints, and so this array is needed to save the model to disk and
  thereby allow users to know what the outcome was in human
  understandable terms. */
  private String[] outcomeLabels;

  /** Stores the String names of the predicates. The GIS only tracks
  predicates as ints, and so this array is needed to save the model to
  disk and thereby allow users to know what the outcome was in human
  understandable terms. */
  private String[] predLabels;

  /** Stores the estimated parameter value of each predicate during iteration. */
  private MutableContext[] params; 

  private int[][][] updates;
  private int VALUE = 0;
  private int ITER = 1;
  private int EVENT = 2;
  
  /** Stores the average parameter values of each predicate during iteration. */
  private MutableContext[] averageParams;

  private EvalParameters evalParams;

  private boolean printMessages = true;
    
  double[] modelDistribution;
  
  private int iterations;
  private boolean useAverage;
  
  public AbstractModel trainModel(int iterations, DataIndexer di, int cutoff) {
    this.iterations = iterations;
    return trainModel(iterations,di,cutoff,true);
  }
  
  public AbstractModel trainModel(int iterations, DataIndexer di, int cutoff, boolean useAverage) {
    display("Incorporating indexed data for training...  \n");
    this.useAverage = useAverage;
    contexts = di.getContexts();
    values = di.getValues();
    numTimesEventsSeen = di.getNumTimesEventsSeen();
    numEvents = di.getNumEvents();
    numUniqueEvents = contexts.length;

    this.iterations = iterations;
    outcomeLabels = di.getOutcomeLabels();
    outcomeList = di.getOutcomeList();

    predLabels = di.getPredLabels();
    numPreds = predLabels.length;
    numOutcomes = outcomeLabels.length;
    if (useAverage) updates = new int[numPreds][numOutcomes][3];
    
    display("done.\n");
    
    display("\tNumber of Event Tokens: " + numUniqueEvents + "\n");
    display("\t    Number of Outcomes: " + numOutcomes + "\n");
    display("\t  Number of Predicates: " + numPreds + "\n");
    

    params = new MutableContext[numPreds];
    if (useAverage) averageParams = new MutableContext[numPreds];
    evalParams = new EvalParameters(params,numOutcomes);
    
    int[] allOutcomesPattern= new int[numOutcomes];
    for (int oi = 0; oi < numOutcomes; oi++) {
      allOutcomesPattern[oi] = oi;
    }
    
    for (int pi = 0; pi < numPreds; pi++) {
      params[pi] = new MutableContext(allOutcomesPattern,new double[numOutcomes]);
      if (useAverage) averageParams[pi] = new MutableContext(allOutcomesPattern,new double[numOutcomes]);
      for (int aoi=0;aoi<numOutcomes;aoi++) {
        params[pi].setParameter(aoi, 0.0);
        if (useAverage) averageParams[pi].setParameter(aoi, 0.0);
      }
    }
    modelDistribution = new double[numOutcomes];

    display("Computing model parameters...\n");
    findParameters(iterations);
    display("...done.\n");

    /*************** Create and return the model ******************/
    if (useAverage) {
      return new PerceptronModel(averageParams, predLabels, outcomeLabels);
    }
    else {
      return new PerceptronModel(params, predLabels, outcomeLabels);
    }
  }

  private void display(String s) {
    if (printMessages)
      System.out.print(s);
  }
  
  private void findParameters(int iterations) {
    display("Performing " + iterations + " iterations.\n");
    for (int i = 1; i <= iterations; i++) {
      if (i < 10)
        display("  " + i + ":  ");
      else if (i < 100)
        display(" " + i + ":  ");
      else
        display(i + ":  ");
      nextIteration(i);
    }
    if (useAverage) {
      trainingStats(averageParams);
    }
    else {
      trainingStats(params);
    }
    // kill a bunch of these big objects now that we don't need them
    numTimesEventsSeen = null;
    contexts = null;
  }
  
  /* Compute one iteration of Perceptron and return log-likelihood.*/
  private void nextIteration(int iteration) {
    iteration--; //move to 0-based index
    int numCorrect = 0;
    int oei = 0;
    for (int ei = 0; ei < numUniqueEvents; ei++,oei++) {
      //Arrays.sort(contexts[ei]); only needed for debugging
      for (int ni=0;ni<this.numTimesEventsSeen[ei];ni++) {
        //System.err.print("contexts["+ei+"]=");for (int ci=0;ci<contexts[ei].length;ci++) { System.err.print(" "+contexts[ei][ci]+" ");} System.err.println();
        for (int oi = 0; oi < numOutcomes; oi++) {
          modelDistribution[oi] = 0;
        }
        if (values != null) {
          PerceptronModel.eval(contexts[ei], values[ei], modelDistribution, evalParams,false);
        }
        else {
          PerceptronModel.eval(contexts[ei], null, modelDistribution, evalParams, false);
        }
        int max = 0;
        for (int oi = 1; oi < numOutcomes; oi++) {
          if (modelDistribution[oi] > modelDistribution[max]) {
            max = oi;
          }
        }
        boolean correct = max == outcomeList[oei]; 
        if (correct) {
          numCorrect ++;
        }
        for (int oi = 0;oi<numOutcomes;oi++) {
          if (oi == outcomeList[oei]) {
            if (modelDistribution[oi] <= 0) {
              for (int ci = 0; ci < contexts[ei].length; ci++) {
                int pi = contexts[ei][ci];
                if (values == null) {
                  params[pi].updateParameter(oi, 1);
                }
                else {
                  params[pi].updateParameter(oi, values[ei][ci]);
                }
                if (useAverage) {
                  if (updates[pi][oi][VALUE] != 0) {
                    averageParams[pi].updateParameter(oi,updates[pi][oi][VALUE]*(numEvents*(iteration-updates[pi][oi][ITER])+(ei-updates[pi][oi][EVENT])));
                    //System.err.println("p avp["+pi+"]."+oi+"="+averageParams[pi].getParameters()[oi]);
                  }
                  //System.err.println("p updates["+pi+"]["+oi+"]=("+updates[pi][oi][ITER]+","+updates[pi][oi][EVENT]+","+updates[pi][oi][VALUE]+") + ("+iteration+","+ei+","+params[pi].getParameters()[oi]+") -> "+averageParams[pi].getParameters()[oi]);
                  updates[pi][oi][VALUE] = (int) params[pi].getParameters()[oi];
                  updates[pi][oi][ITER] = iteration;
                  updates[pi][oi][EVENT] = ei;
                }
              }
            }
          }
          else {
            if (modelDistribution[oi] > 0) {
              for (int ci = 0; ci < contexts[ei].length; ci++) {
                int pi = contexts[ei][ci];
                if (values == null) {
                  params[pi].updateParameter(oi, -1);
                }
                else {
                  params[pi].updateParameter(oi, -1*values[ei][ci]);
                }
                if (useAverage) {
                  if (updates[pi][oi][VALUE] != 0) {
                    averageParams[pi].updateParameter(oi,updates[pi][oi][VALUE]*(numEvents*(iteration-updates[pi][oi][ITER])+(ei-updates[pi][oi][EVENT])));
                    //System.err.println("d avp["+pi+"]."+oi+"="+averageParams[pi].getParameters()[oi]);
                  }
                  //System.err.println(ei+" d updates["+pi+"]["+oi+"]=("+updates[pi][oi][ITER]+","+updates[pi][oi][EVENT]+","+updates[pi][oi][VALUE]+") + ("+iteration+","+ei+","+params[pi].getParameters()[oi]+") -> "+averageParams[pi].getParameters()[oi]);
                  updates[pi][oi][VALUE] = (int) params[pi].getParameters()[oi];
                  updates[pi][oi][ITER] = iteration;
                  updates[pi][oi][EVENT] = ei;
                }
              }
            }
          }
        }
      }
    }
    //finish average computation
    double totIterations = (double) iterations*numEvents;
    if (useAverage && iteration == iterations-1) {
      for (int pi = 0; pi < numPreds; pi++) {
        double[] predParams = averageParams[pi].getParameters();
        for (int oi = 0;oi<numOutcomes;oi++) {
          if (updates[pi][oi][VALUE] != 0) {
            predParams[oi] +=  updates[pi][oi][VALUE]*(numEvents*(iterations-updates[pi][oi][ITER])-updates[pi][oi][EVENT]);
          }
          if (predParams[oi] != 0) {
            predParams[oi] /=totIterations;  
            averageParams[pi].setParameter(oi, predParams[oi]);
            //System.err.println("updates["+pi+"]["+oi+"]=("+updates[pi][oi][ITER]+","+updates[pi][oi][EVENT]+","+updates[pi][oi][VALUE]+") + ("+iterations+","+0+","+params[pi].getParameters()[oi]+") -> "+averageParams[pi].getParameters()[oi]);
          }
        }
      }
    }
    display(". ("+numCorrect+"/"+numEvents+") "+((double) numCorrect / numEvents) + "\n");
  }  
  
  private void trainingStats(MutableContext[] params) {
    int numCorrect = 0;
    for (int ei = 0; ei < numUniqueEvents; ei++) {
      for (int ni=0;ni<this.numTimesEventsSeen[ei];ni++) {
        for (int oi = 0; oi < numOutcomes; oi++) {
          modelDistribution[oi] = 0;
        }
        if (values != null) {
          PerceptronModel.eval(contexts[ei], values[ei], modelDistribution, evalParams,false);
        }
        else {
          PerceptronModel.eval(contexts[ei], null, modelDistribution, evalParams, false);
        }
        int max = 0;
        for (int oi = 1; oi < numOutcomes; oi++) {
          if (modelDistribution[oi] > modelDistribution[max]) {
            max = oi;
          }
        }
        if (max == outcomeList[ei]) {
          numCorrect ++;
        }
      }
    }
    display(". ("+numCorrect+"/"+numEvents+") "+((double) numCorrect / numEvents) + "\n");
  }
}
