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

import static org.junit.Assert.assertEquals;
import opennlp.tools.util.Span;

import org.junit.Test;

/**
 * Tests for the {@link FMeasure} class.
 */
public class FMeasureTest {

  private static final double DELTA = 1.0E-9d;
  
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
  
  private Span predictedCompletelyDistinct[] = {
      new Span(100, 120),
      new Span(210, 220),
      new Span(211, 220),
      new Span(212, 220),
      new Span(220, 230)
  };

  /**
   * Test for the {@link EvaluatorUtil#countTruePositives(Span[], Span[])} method.
   */
  @Test
  public void testCountTruePositives() {
    assertEquals(0, FMeasure.countTruePositives(new Object[]{}, new Object[]{}));
    assertEquals(gold.length, FMeasure.countTruePositives(gold, gold));
    assertEquals(0, FMeasure.countTruePositives(gold, predictedCompletelyDistinct));
    assertEquals(2, FMeasure.countTruePositives(gold, predicted));
  }

  /**
   * Test for the {@link EvaluatorUtil#precision(Span[], Span[])} method.
   */
  @Test
  public void testPrecision() {
    assertEquals(1.0d, FMeasure.precision(gold, gold), DELTA);
    assertEquals(0, FMeasure.precision(gold, predictedCompletelyDistinct), DELTA);
    assertEquals(Double.NaN, FMeasure.precision(gold, new Object[]{}), DELTA);
    assertEquals(0, FMeasure.precision(new Object[]{}, gold), DELTA);
    assertEquals(2d / predicted.length, FMeasure.precision(gold, predicted), DELTA);
  }

  /**
   * Test for the {@link EvaluatorUtil#recall(Span[], Span[])} method.
   */
  @Test
  public void testRecall() {
    assertEquals(1.0d, FMeasure.recall(gold, gold), DELTA);
    assertEquals(0, FMeasure.recall(gold, predictedCompletelyDistinct), DELTA);
    assertEquals(0, FMeasure.recall(gold, new Object[]{}), DELTA);
    assertEquals(Double.NaN, FMeasure.recall(new Object[]{}, gold), DELTA);
    assertEquals(2d / gold.length, FMeasure.recall(gold, predicted), DELTA);
  }
}