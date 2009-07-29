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

package opennlp.tools.util;

import junit.framework.TestCase;

/**
 * Tests for the {@link FMeasure} class.
 */
public class FMeasureTest extends TestCase {

  private Span gold[] = {
      new Span(8, 9),
      new Span(9, 10),
      new Span(10, 12),
      new Span(13, 14),
      new Span(14, 15),
      new Span(15, 16)
  };

  private Span predicted[] = {
      new Span(14, 15),
      new Span(15, 16),
      new Span(100, 120),
      new Span(210, 220),
      new Span(220, 230)
  };

  /**
   * Test for the {@link EvaluatorUtil#countTruePositives(Span[], Span[])} method.
   */
  public void testCountTruePositives() {
    assertEquals(2, FMeasure.countTruePositives(gold, predicted));
  }

  /**
   * Test for the {@link EvaluatorUtil#precision(Span[], Span[])} method.
   */
  public void testPrecision() {
    assertEquals(2d / predicted.length, FMeasure.precision(gold, predicted));
  }

  /**
   * Test for the {@link EvaluatorUtil#recall(Span[], Span[])} method.
   */
  public void testRecall() {
    assertEquals(2d / gold.length, FMeasure.recall(gold, predicted));
  }
}