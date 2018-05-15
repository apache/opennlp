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

package opennlp.tools.ml.maxent.quasinewton;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import opennlp.tools.ml.ArrayMath;
import opennlp.tools.ml.model.DataIndexer;

/**
 * Evaluate negative log-likelihood and its gradient in parallel
 */
public class ParallelNegLogLikelihood extends NegLogLikelihood {

  // Number of threads
  private int threads;

  // Partial value of negative log-likelihood to be computed by each thread
  private double[] negLogLikelihoodThread;

  // Partial gradient
  private double[][] gradientThread;

  public ParallelNegLogLikelihood(DataIndexer indexer, int threads) {
    super(indexer);

    if (threads <= 0)
      throw new IllegalArgumentException(
          "Number of threads must 1 or larger");

    this.threads                = threads;
    this.negLogLikelihoodThread = new double[threads];
    this.gradientThread         = new double[threads][dimension];
  }

  /**
   * Negative log-likelihood
   */
  @Override
  public double valueAt(double[] x) {

    if (x.length != dimension)
      throw new IllegalArgumentException(
          "x is invalid, its dimension is not equal to domain dimension.");

    // Compute partial value of negative log-likelihood in each thread
    computeInParallel(x, NegLLComputeTask.class);

    double negLogLikelihood = 0;
    for (int t = 0; t < threads; t++) {
      negLogLikelihood += negLogLikelihoodThread[t];
    }

    return negLogLikelihood;
  }

  /**
   * Compute gradient
   */
  @Override
  public double[] gradientAt(double[] x) {

    if (x.length != dimension)
      throw new IllegalArgumentException(
          "x is invalid, its dimension is not equal to the function.");

    // Compute partial gradient in each thread
    computeInParallel(x, GradientComputeTask.class);

    // Accumulate gradient
    for (int i = 0; i < dimension; i++) {
      gradient[i] = 0;
      for (int t = 0; t < threads; t++) {
        gradient[i] += gradientThread[t][i];
      }
    }

    return gradient;
  }

  /**
   * Compute tasks in parallel
   */
  private void computeInParallel(double[] x, Class<? extends ComputeTask> taskClass) {
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    int taskSize = numContexts / threads;
    int leftOver = numContexts % threads;

    try {
      Constructor<? extends ComputeTask> cons = taskClass.getConstructor(
          ParallelNegLogLikelihood.class,
          int.class, int.class, int.class, double[].class);

      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        if (i != threads - 1)
          futures.add(executor.submit(
              cons.newInstance(this, i, i * taskSize, taskSize, x)));
        else
          futures.add(executor.submit(
              cons.newInstance(this, i, i * taskSize, taskSize + leftOver, x)));
      }

      for (Future<?> future: futures)
        future.get();

    } catch (Exception e) {
      e.printStackTrace();
    }

    executor.shutdown();
  }

  /**
   * Task that is computed in parallel
   */
  abstract class ComputeTask implements Callable<ComputeTask> {

    final int threadIndex;

    // Start index of contexts to compute
    final int startIndex;

    // Number of contexts to compute
    final int length;

    final double[] x;

    public ComputeTask(int threadIndex, int startIndex, int length, double[] x) {
      this.threadIndex = threadIndex;
      this.startIndex  = startIndex;
      this.length      = length;
      this.x           = x;
    }
  }

  /**
   * Task for computing partial value of negative log-likelihood
   */
  class NegLLComputeTask extends ComputeTask {

    final double[] tempSums;

    public NegLLComputeTask(int threadIndex, int startIndex, int length, double[] x) {
      super(threadIndex, startIndex, length, x);
      this.tempSums = new double[numOutcomes];
    }

    @Override
    public NegLLComputeTask call() {
      int ci, oi, ai, vectorIndex, outcome;
      double predValue, logSumOfExps;
      negLogLikelihoodThread[threadIndex] = 0;

      for (ci = startIndex; ci < startIndex + length; ci++) {
        for (oi = 0; oi < numOutcomes; oi++) {
          tempSums[oi] = 0;
          for (ai = 0; ai < contexts[ci].length; ai++) {
            vectorIndex = indexOf(oi, contexts[ci][ai]);
            predValue = values != null ? values[ci][ai] : 1.0;
            tempSums[oi] += predValue * x[vectorIndex];
          }
        }

        logSumOfExps = ArrayMath.logSumOfExps(tempSums);

        outcome = outcomeList[ci];
        negLogLikelihoodThread[threadIndex] -=
            (tempSums[outcome] - logSumOfExps) * numTimesEventsSeen[ci];
      }

      return this;
    }
  }

  /**
   * Task for computing partial gradient
   */
  class GradientComputeTask extends ComputeTask {

    final double[] expectation;

    public GradientComputeTask(int threadIndex, int startIndex, int length, double[] x) {
      super(threadIndex, startIndex, length, x);
      this.expectation = new double[numOutcomes];
    }

    @Override
    public GradientComputeTask call() {
      int ci, oi, ai, vectorIndex;
      double predValue, logSumOfExps;
      int empirical;

      // Reset gradientThread
      Arrays.fill(gradientThread[threadIndex], 0);

      for (ci = startIndex; ci < startIndex + length; ci++) {
        for (oi = 0; oi < numOutcomes; oi++) {
          expectation[oi] = 0;
          for (ai = 0; ai < contexts[ci].length; ai++) {
            vectorIndex = indexOf(oi, contexts[ci][ai]);
            predValue = values != null ? values[ci][ai] : 1.0;
            expectation[oi] += predValue * x[vectorIndex];
          }
        }

        logSumOfExps = ArrayMath.logSumOfExps(expectation);

        for (oi = 0; oi < numOutcomes; oi++) {
          expectation[oi] = Math.exp(expectation[oi] - logSumOfExps);
        }

        for (oi = 0; oi < numOutcomes; oi++) {
          empirical = outcomeList[ci] == oi ? 1 : 0;
          for (ai = 0; ai < contexts[ci].length; ai++) {
            vectorIndex = indexOf(oi, contexts[ci][ai]);
            predValue = values != null ? values[ci][ai] : 1.0;
            gradientThread[threadIndex][vectorIndex] +=
                predValue * (expectation[oi] - empirical) * numTimesEventsSeen[ci];
          }
        }
      }

      return this;
    }
  }
}
