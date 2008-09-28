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

package opennlp.maxent;

import opennlp.model.DataIndexer;
import opennlp.model.EvalParameters;
import opennlp.model.EventStream;
import opennlp.model.MutableContext;
import opennlp.model.OnePassDataIndexer;
import opennlp.model.Prior;
import opennlp.model.UniformPrior;


/**
 * An implementation of Generalized Iterative Scaling.  The reference paper
 * for this implementation was Adwait Ratnaparkhi's tech report at the
 * University of Pennsylvania's Institute for Research in Cognitive Science,
 * and is available at <a href ="ftp://ftp.cis.upenn.edu/pub/ircs/tr/97-08.ps.Z"><code>ftp://ftp.cis.upenn.edu/pub/ircs/tr/97-08.ps.Z</code></a>. 
 *
 * The slack parameter used in the above implementation has been removed by default
 * from the computation and a method for updating with Gaussian smoothing has been
 * added per Investigating GIS and Smoothing for Maximum Entropy Taggers, Clark and Curran (2002).  
 * <a href="http://acl.ldc.upenn.edu/E/E03/E03-1071.pdf"><code>http://acl.ldc.upenn.edu/E/E03/E03-1071.pdf</code></a>
 * The slack parameter can be used by setting <code>useSlackParameter</code> to true.
 * Gaussian smoothing can be used by setting <code>useGaussianSmoothing</code> to true. 
 * 
 * A prior can be used to train models which converge to the distribution which minimizes the
 * relative entropy between the distribution specified by the emperical constraints of the training
 * data and the specified prior.  By default, the uniform distribution is used as the prior.
 *    
 * @author Tom Morton
 * @author  Jason Baldridge
 * @version $Revision: 1.30 $, $Date: 2008-09-28 18:03:38 $
 */
class GISTrainer {

  /**
   * Specifies whether unseen context/outcome pairs should be estimated as occur very infrequently.
   */
  private boolean useSimpleSmoothing = false;
  /**
   * Specifies whether a slack parameter should be used in the model.
   */
  private boolean useSlackParameter = false;
  /** Specified whether parameter updates should prefer a distribution of parameters which
   * is gaussian.
   */
  private boolean useGaussianSmoothing = false;
  private double sigma = 2.0;

  // If we are using smoothing, this is used as the "number" of
  // times we want the trainer to imagine that it saw a feature that it
  // actually didn't see.  Defaulted to 0.1.
  private double _smoothingObservation = 0.1;

  private boolean printMessages = false;

  /** Number of unique events which occured in the event set. */
  private int numUniqueEvents; 
  /** Number of predicates. */
  private int numPreds; 
  /** Number of outcomes. */
  private int numOutcomes; 

  /** Records the array of predicates seen in each event. */
  private int[][] contexts;
  
  /** The value associated with each context. If null then context values are assumes to be 1. */
  private float[][] values;
  
  /** List of outcomes for each event i, in context[i]. */
  private int[] outcomeList;

  /** Records the num of times an event has been seen for each event i, in context[i]. */
  private int[] numTimesEventsSeen;
  
  /** The number of times a predicate occured in the training data. */
  private int[] predicateCounts;
  
  private int cutoff;

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

  /** Stores the observed expected values of the features based on training data. */
  private MutableContext[] observedExpects;

  /** Stores the estimated parameter value of each predicate during iteration */
  private MutableContext[] params;

  /** Stores the expected values of the features based on the current models */
  private MutableContext[] modelExpects;
  
  /** This is the prior distribution that the model uses for training. */
  private Prior prior;


  /** Observed expectation of correction feature. */
  private double cfObservedExpect;
  /** A global variable for the models expected value of the correction feature. */
  private double CFMOD;

  private final double NEAR_ZERO = 0.01;
  private final double LLThreshold = 0.0001;

  /** Stores the output of the current model on a single event durring
   *  training.  This we be reset for every event for every itteration.  */
  double[] modelDistribution;
  /** Stores the number of features that get fired per event. */
  int[] numfeats;
  /** Initial probability for all outcomes. */
  
  EvalParameters evalParams;
  /**
   * Creates a new <code>GISTrainer</code> instance which does
   * not print progress messages about training to STDOUT.
   *
   */
  GISTrainer() {
    super();
  }

  /**
   * Creates a new <code>GISTrainer</code> instance.
   *
   * @param printMessages sends progress messages about training to
   *                      STDOUT when true; trains silently otherwise.
   */
  GISTrainer(boolean printMessages) {
    this();
    this.printMessages = printMessages;
  }

  /**
   * Sets whether this trainer will use smoothing while training the model.
   * This can improve model accuracy, though training will potentially take
   * longer and use more memory.  Model size will also be larger.
   *
   * @param smooth true if smoothing is desired, false if not
   */
  public void setSmoothing(boolean smooth) {
    useSimpleSmoothing = smooth;
  }

  /**
   * Sets whether this trainer will use smoothing while training the model.
   * This can improve model accuracy, though training will potentially take
   * longer and use more memory.  Model size will also be larger.
   *
   * @param timesSeen the "number" of times we want the trainer to imagine
   *                  it saw a feature that it actually didn't see
   */
  public void setSmoothingObservation(double timesSeen) {
    _smoothingObservation = timesSeen;
  }
  

  /**
   * Trains a GIS model on the event in the specified event stream, using the specified number
   * of iterations and the specified count cutoff.
   * @param eventStream A stream of all events.
   * @param iterations The number of iterations to use for GIS.
   * @param cutoff The number of times a feature must occur to be included.
   * @return A GIS model trained with specified 
   */
  public GISModel trainModel(EventStream eventStream, int iterations, int cutoff) {
    return trainModel(iterations, new OnePassDataIndexer(eventStream,cutoff),cutoff);
  }
  
  /**
   * Train a model using the GIS algorithm.
   *
   * @param iterations  The number of GIS iterations to perform.
   * @param di The data indexer used to compress events in memory.
   * @return The newly trained model, which can be used immediately or saved
   *         to disk using an opennlp.maxent.io.GISModelWriter object.
   */
  public GISModel trainModel(int iterations, DataIndexer di, int cutoff) {
    return trainModel(iterations,di,new UniformPrior(),cutoff);
  }

  /**
   * Train a model using the GIS algorithm.
   *
   * @param iterations  The number of GIS iterations to perform.
   * @param di The data indexer used to compress events in memory.
   * @param modelPrior The prior distribution used to train this model.
   * @return The newly trained model, which can be used immediately or saved
   *         to disk using an opennlp.maxent.io.GISModelWriter object.
   */
  public GISModel trainModel(int iterations, DataIndexer di, Prior modelPrior, int cutoff) {
    /************** Incorporate all of the needed info ******************/
    display("Incorporating indexed data for training...  \n");
    contexts = di.getContexts();
    values = di.getValues();
    this.cutoff = cutoff;
    predicateCounts = di.getPredCounts();
    numTimesEventsSeen = di.getNumTimesEventsSeen();
    numUniqueEvents = contexts.length;
    this.prior = modelPrior;
    //printTable(contexts);

    // determine the correction constant and its inverse
    int correctionConstant = 1;
    for (int ci = 0; ci < contexts.length; ci++) {
      if (values == null || values[ci] == null) {
        if (contexts[ci].length > correctionConstant) {
          correctionConstant = contexts[ci].length;
        }
      }
      else {
        float cl = values[ci][0];
        for (int vi=1;vi<values[ci].length;vi++) {
          cl+=values[ci][vi];
        }
        
        if (cl > correctionConstant) {
          correctionConstant=(int) Math.ceil(cl);
        }
      }
    }
    display("done.\n");

    outcomeLabels = di.getOutcomeLabels();
    outcomeList = di.getOutcomeList();
    numOutcomes = outcomeLabels.length;

    predLabels = di.getPredLabels();
    prior.setLabels(outcomeLabels,predLabels);
    numPreds = predLabels.length;

    display("\tNumber of Event Tokens: " + numUniqueEvents + "\n");
    display("\t    Number of Outcomes: " + numOutcomes + "\n");
    display("\t  Number of Predicates: " + numPreds + "\n");

    // set up feature arrays
    float[][] predCount = new float[numPreds][numOutcomes];
    for (int ti = 0; ti < numUniqueEvents; ti++) {
      for (int j = 0; j < contexts[ti].length; j++) {
        if (values != null && values[ti] != null) {
          predCount[contexts[ti][j]][outcomeList[ti]] += numTimesEventsSeen[ti]*values[ti][j];
        }
        else {          
          predCount[contexts[ti][j]][outcomeList[ti]] += numTimesEventsSeen[ti];
        }
      }
    }

    //printTable(predCount);
    di = null; // don't need it anymore

    // A fake "observation" to cover features which are not detected in
    // the data.  The default is to assume that we observed "1/10th" of a
    // feature during training.
    final double smoothingObservation = _smoothingObservation;

    // Get the observed expectations of the features. Strictly speaking,
    // we should divide the counts by the number of Tokens, but because of
    // the way the model's expectations are approximated in the
    // implementation, this is cancelled out when we compute the next
    // iteration of a parameter, making the extra divisions wasteful.
    params = new MutableContext[numPreds];
    modelExpects = new MutableContext[numPreds];
    observedExpects = new MutableContext[numPreds];
    evalParams = new EvalParameters(params,0,correctionConstant,numOutcomes);
    int[] activeOutcomes = new int[numOutcomes];
    int[] outcomePattern;
    int[] allOutcomesPattern= new int[numOutcomes];
    for (int oi = 0; oi < numOutcomes; oi++) {
      allOutcomesPattern[oi] = oi;
    }
    int numActiveOutcomes = 0;
    for (int pi = 0; pi < numPreds; pi++) {
      numActiveOutcomes = 0;
      if (useSimpleSmoothing) {
        numActiveOutcomes = numOutcomes;
        outcomePattern = allOutcomesPattern;
      }
      else { //determine active outcomes
        for (int oi = 0; oi < numOutcomes; oi++) {
          if (predCount[pi][oi] > 0 && predicateCounts[pi] > cutoff) {
            activeOutcomes[numActiveOutcomes] = oi;
            numActiveOutcomes++;
          }
        }
        if (numActiveOutcomes == numOutcomes) {
          outcomePattern = allOutcomesPattern;
        }
        else {
          outcomePattern = new int[numActiveOutcomes];
          for (int aoi=0;aoi<numActiveOutcomes;aoi++) {
            outcomePattern[aoi] = activeOutcomes[aoi];
          }
        }
      }
      params[pi] = new MutableContext(outcomePattern,new double[numActiveOutcomes]);
      modelExpects[pi] = new MutableContext(outcomePattern,new double[numActiveOutcomes]);
      observedExpects[pi] = new MutableContext(outcomePattern,new double[numActiveOutcomes]);
      for (int aoi=0;aoi<numActiveOutcomes;aoi++) {
        int oi = outcomePattern[aoi];
        params[pi].setParameter(aoi, 0.0);
        modelExpects[pi].setParameter(aoi, 0.0);
        if (predCount[pi][oi] > 0) {
            observedExpects[pi].setParameter(aoi, predCount[pi][oi]);
        }
        else if (useSimpleSmoothing) { 
          observedExpects[pi].setParameter(aoi,smoothingObservation);
        }
      }
    }

    // compute the expected value of correction
    if (useSlackParameter) {
      int cfvalSum = 0;
      for (int ti = 0; ti < numUniqueEvents; ti++) {
        for (int j = 0; j < contexts[ti].length; j++) {
          int pi = contexts[ti][j];
          if (!modelExpects[pi].contains(outcomeList[ti])) {
            cfvalSum += numTimesEventsSeen[ti];
          }
        }
        cfvalSum += (correctionConstant - contexts[ti].length) * numTimesEventsSeen[ti];
      }
      if (cfvalSum == 0) {
        cfObservedExpect = Math.log(NEAR_ZERO); //nearly zero so log is defined
      }
      else {
        cfObservedExpect = Math.log(cfvalSum);
      }
    }
    predCount = null; // don't need it anymore

    display("...done.\n");

    modelDistribution = new double[numOutcomes];
    numfeats = new int[numOutcomes];

    /***************** Find the parameters ************************/
    display("Computing model parameters...\n");
    findParameters(iterations);

    /*************** Create and return the model ******************/
    return new GISModel(params, predLabels, outcomeLabels, correctionConstant, evalParams.correctionParam);

  }

  /* Estimate and return the model parameters. */
  private void findParameters(int iterations) {
    double prevLL = 0.0;
    double currLL = 0.0;
    display("Performing " + iterations + " iterations.\n");
    for (int i = 1; i <= iterations; i++) {
      if (i < 10)
        display("  " + i + ":  ");
      else if (i < 100)
        display(" " + i + ":  ");
      else
        display(i + ":  ");
      currLL = nextIteration();
      if (i > 1) {
        if (prevLL > currLL) {
          System.err.println("Model Diverging: loglikelihood decreased");
          break;
        }
        if (currLL - prevLL < LLThreshold) {
          break;
        }
      }
      prevLL = currLL;
    }

    // kill a bunch of these big objects now that we don't need them
    observedExpects = null;
    modelExpects = null;
    numTimesEventsSeen = null;
    contexts = null;
  }
  
  //modeled on implementation in  Zhang Le's maxent kit
  private double gaussianUpdate(int predicate, int oid, int n, double correctionConstant) {
    double param = params[predicate].getParameters()[oid];
    double x = 0.0;
    double x0 = 0.0;
    double f;
    double tmp;
    double fp;
    double modelValue = modelExpects[predicate].getParameters()[oid];
    double observedValue = observedExpects[predicate].getParameters()[oid];
    for (int i = 0; i < 50; i++) {
      tmp = modelValue * Math.exp(correctionConstant * x0);
      f = tmp + (param + x0) / sigma - observedValue;
      fp = tmp * correctionConstant + 1 / sigma;
      if (fp == 0) {
        break;
      }
      x = x0 - f / fp;
      if (Math.abs(x - x0) < 0.000001) {
        x0 = x;
        break;
      }
      x0 = x;
    }
    return x0;
  }
  
  /* Compute one iteration of GIS and retutn log-likelihood.*/
  private double nextIteration() {
    // compute contribution of p(a|b_i) for each feature and the new
    // correction parameter
    double loglikelihood = 0.0;
    CFMOD = 0.0;
    int numEvents = 0;
    int numCorrect = 0;
    for (int ei = 0; ei < numUniqueEvents; ei++) {
      if (values != null) {
        prior.logPrior(modelDistribution,contexts[ei],values[ei]);
        GISModel.eval(contexts[ei], values[ei], modelDistribution, evalParams);
      }
      else {
        prior.logPrior(modelDistribution,contexts[ei]);
        GISModel.eval(contexts[ei], modelDistribution, evalParams);
      }
      for (int j = 0; j < contexts[ei].length; j++) {
        int pi = contexts[ei][j];
        if (predicateCounts[pi] >= cutoff) {
          int[] activeOutcomes = modelExpects[pi].getOutcomes();
          for (int aoi=0;aoi<activeOutcomes.length;aoi++) {
            int oi = activeOutcomes[aoi];
            if (values != null && values[ei] != null) {
              modelExpects[pi].updateParameter(aoi,modelDistribution[oi] * values[ei][j] * numTimesEventsSeen[ei]);
            }
            else {
              modelExpects[pi].updateParameter(aoi,modelDistribution[oi] * numTimesEventsSeen[ei]);
            }
          }
          if (useSlackParameter) {
            for (int oi = 0; oi < numOutcomes; oi++) {
              if (!modelExpects[pi].contains(oi)) {
                CFMOD += modelDistribution[oi] * numTimesEventsSeen[ei];
              }
            }
          }
        }
      }
      if (useSlackParameter)
        CFMOD += (evalParams.correctionConstant - contexts[ei].length) * numTimesEventsSeen[ei];
      
      loglikelihood += Math.log(modelDistribution[outcomeList[ei]]) * numTimesEventsSeen[ei];
      numEvents += numTimesEventsSeen[ei];
      if (printMessages) {
        int max = 0;
        for (int oi = 1; oi < numOutcomes; oi++) {
          if (modelDistribution[oi] > modelDistribution[max]) {
            max = oi;
          }
        }
        if (max == outcomeList[ei]) {
          numCorrect += numTimesEventsSeen[ei];
        }
      }

    }
    display(".");

    // compute the new parameter values
    for (int pi = 0; pi < numPreds; pi++) {
      double[] observed = observedExpects[pi].getParameters();
      double[] model = modelExpects[pi].getParameters();
      int[] activeOutcomes = params[pi].getOutcomes();
      for (int aoi=0;aoi<activeOutcomes.length;aoi++) {
        if (useGaussianSmoothing) {
          params[pi].updateParameter(aoi,gaussianUpdate(pi,aoi,numEvents,evalParams.correctionConstant));
        }
        else {
          if (model[aoi] == 0) {
            System.err.println("Model expects == 0 for "+predLabels[pi]+" "+outcomeLabels[aoi]);
          }
          params[pi].updateParameter(aoi,(Math.log(observed[aoi]) - Math.log(model[aoi])));
        }
        modelExpects[pi].setParameter(aoi,0.0); // re-initialize to 0.0's
      }
    }
    if (CFMOD > 0.0 && useSlackParameter)
        evalParams.correctionParam += (cfObservedExpect - Math.log(CFMOD));

    display(". loglikelihood=" + loglikelihood + "\t" + ((double) numCorrect / numEvents) + "\n");
    return (loglikelihood);
  }

  private void display(String s) {
    if (printMessages)
      System.out.print(s);
  }

}
