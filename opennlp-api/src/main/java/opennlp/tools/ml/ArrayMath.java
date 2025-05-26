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

package opennlp.tools.ml;

import java.util.List;

import opennlp.tools.ml.model.Context;

/**
 * Utility class for simple vector arithmetic.
 */
public class ArrayMath {

  public static double innerProduct(double[] vecA, double[] vecB) {
    if (vecA == null || vecB == null || vecA.length != vecB.length)
      return Double.NaN;

    double product = 0.0;
    for (int i = 0; i < vecA.length; i++) {
      product += vecA[i] * vecB[i];
    }
    return product;
  }

  /**
   * Computes the L1-norm for a vector {@code v}.
   *
   * @param v The vector of {@code double} values.
   * @return The computed L1-norm.
   */
  public static double l1norm(double[] v) {
    double norm = 0;
    for (double value : v) norm += StrictMath.abs(value);
    return norm;
  }

  /**
   * Computes the L2-norm for a vector {@code v}.
   *
   * @param v The vector of {@code double} values.
   * @return The computed L2-norm.
   */
  public static double l2norm(double[] v) {
    return StrictMath.sqrt(innerProduct(v, v));
  }

  /**
   * Computes the Inverse L2-norm for a vector {@code v}.
   *
   * @param v The vector of {@code double} values.
   * @return The computed Inverse L2-norm.
   */
  public static double invL2norm(double[] v) {
    return 1 / l2norm(v);
  }

  /**
   * Computes {@code \log(\sum_{i=1}^n e^{x_i})} using a maximum-element trick
   * to avoid arithmetic overflow.
   *
   * @param x The vector of {@code double} values.
   * @return The log-sum of exponentials of vector elements.
   */
  public static double logSumOfExps(double[] x) {
    double max = max(x);
    double sum = 0.0;
    for (double v : x) {
      if (v != Double.NEGATIVE_INFINITY)
        sum += StrictMath.exp(v - max);
    }
    return max + StrictMath.log(sum);
  }

  /**
   * Finds the maximum element in a vector {@code x}.
   * @param x The vector of {@code double} values.
   * @return The maximum element in {@code x}.
   */
  public static double max(double[] x) {
    int maxIdx = argmax(x);
    return x[maxIdx];
  }

  /**
   * Finds the index of the maximum element in a vector {@code x}
   * @param x The vector of {@code double} values.
   * @return The index of the maximum element. Index of the first
   *     maximum element is returned if multiple maximums are found.
   */
  public static int argmax(double[] x) {
    if (x == null || x.length == 0) {
      throw new IllegalArgumentException("Vector x is null or empty");
    }

    int maxIdx = 0;
    for (int i = 1; i < x.length; i++) {
      if (x[maxIdx] < x[i])
        maxIdx = i;
    }
    return maxIdx;
  }

  public static void sumFeatures(Context[] context, float[] values, double[] prior) {
    for (int ci = 0; ci < context.length; ci++) {
      if (context[ci] != null) {
        Context predParams = context[ci];
        int[] activeOutcomes = predParams.getOutcomes();
        double[] activeParameters = predParams.getParameters();
        double value = 1;
        if (values != null) {
          value = values[ci];
        }
        for (int ai = 0; ai < activeOutcomes.length; ai++) {
          int oid = activeOutcomes[ai];
          prior[oid] += activeParameters[ai] * value;
        }
      }
    }
  }

  // === Not really related to math ===
  /**
   * Convert a list of {@link Double} objects into an array of primitive doubles.
   *
   * @param list The input vector of {@link Double values}.
   * @return The {@code double[]}.
   */
  public static double[] toDoubleArray(List<Double> list) {
    double[] arr = new double[list.size()];
    for (int i = 0; i < arr.length; i++) {
      arr[i] = list.get(i);
    }
    return arr;
  }

  /**
   * Convert a list of {@link Integer} objects into an array of primitive integers.
   *
   * @param list The input vector of {@link Integer values}.
   * @return The {@code int[]}.
   */
  public static int[] toIntArray(List<Integer> list) {
    int[] arr = new int[list.size()];
    for (int i = 0; i < arr.length; i++) {
      arr[i] = list.get(i);
    }
    return arr;
  }
}

