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

package opennlp.tools.util.eval;

/**
 * Calculates the arithmetic mean of values
 * added with the {@link #add(double)} method.
 */
public class Mean {

  /**
   * The sum of all added values.
   */
  private double sum;

  /**
   * The number of times a value was added.
   */
  private long count;

  /**
   * Adds a value to the arithmetic mean.
   *
   * @param value the value which should be added
   *     to the arithmetic mean.
   */
  public void add(double value) {
    add(value, 1);
  }

  /**
   * Adds a value count times to the arithmetic mean.
   *
   * @param value the value which should be added
   *     to the arithmetic mean.
   *
   * @param count number of times the value should be added to
   *     arithmetic mean.
   */
  public void add(double value, long count) {
    sum += value * count;
    this.count += count;
  }

  /**
   * Retrieves the mean of all values added with
   * {@link #add(double)} or 0 if there are zero added
   * values.
   */
  public double mean() {
    return count > 0 ? sum / count : 0;
  }

  /**
   * Retrieves the number of times a value
   * was added to the mean.
   */
  public long count() {
    return count;
  }

  @Override
  public String toString() {
    return Double.toString(mean());
  }
}
