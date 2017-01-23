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

import java.util.List;

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
   * L1-norm
   */
  public static double l1norm(double[] v) {
    double norm = 0;
    for (int i = 0; i < v.length; i++)
      norm += Math.abs(v[i]);
    return norm;
  }

  /**
   * L2-norm
   */
  public static double l2norm(double[] v) {
    return Math.sqrt(innerProduct(v, v));
  }

  /**
   * Inverse L2-norm
   */
  public static double invL2norm(double[] v) {
    return 1 / l2norm(v);
  }

  /**
   * Computes \log(\sum_{i=1}^n e^{x_i}) using a maximum-element trick
   * to avoid arithmetic overflow.
   *
   * @param x input vector
   * @return log-sum of exponentials of vector elements
   */
  public static double logSumOfExps(double[] x) {
    double max = max(x);
    double sum = 0.0;
    for (int i = 0; i < x.length; i++) {
      if (x[i] != Double.NEGATIVE_INFINITY)
        sum += Math.exp(x[i] - max);
    }
    return max + Math.log(sum);
  }

  public static double max(double[] x) {
    int maxIdx = maxIdx(x);
    return x[maxIdx];
  }

  /**
   * Find index of maximum element in the vector x
   * @param x input vector
   * @return index of the maximum element. Index of the first
   *     maximum element is returned if multiple maximums are found.
   */
  public static int maxIdx(double[] x) {
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

  // === Not really related to math ===
  /**
   * Convert a list of Double objects into an array of primitive doubles
   */
  public static double[] toDoubleArray(List<Double> list) {
    double[] arr = new double[list.size()];
    for (int i = 0; i < arr.length; i++) {
      arr[i] = list.get(i);
    }
    return arr;
  }

  /**
   *  Convert a list of Integer objects into an array of primitive integers
   */
  public static int[] toIntArray(List<Integer> list) {
    int[] arr = new int[list.size()];
    for (int i = 0; i < arr.length; i++) {
      arr[i] = list.get(i);
    }
    return arr;
  }
}

