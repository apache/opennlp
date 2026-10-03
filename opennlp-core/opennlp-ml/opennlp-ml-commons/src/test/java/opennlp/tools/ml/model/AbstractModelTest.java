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

package opennlp.tools.ml.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractModelTest {

  private static final int THREADS = 8;
  private static final int ITERATIONS = 2_000;

  private static AbstractModel model(String... outcomeNames) {
    return new AbstractModel(new Context[0], new String[0], outcomeNames) {
      @Override
      public double[] eval(String[] context) {
        throw new UnsupportedOperationException();
      }

      @Override
      public double[] eval(String[] context, double[] probs) {
        throw new UnsupportedOperationException();
      }

      @Override
      public double[] eval(String[] context, float[] values) {
        throw new UnsupportedOperationException();
      }
    };
  }

  @ParameterizedTest
  @CsvSource({
      "0.0, 0.0000",
      "1.0, 1.0000",
      "0.5, 0.5000",
      "0.3333333333333333, 0.3333",
      "0.6666666666666666, 0.6667",
      // 1/32 and 3/32 are exact in binary and pin half-even rounding
      "0.03125, 0.0312",
      "0.09375, 0.0938",
      "1.0E-9, 0.0000",
      "12.5, 12.5000"
  })
  void testGetAllOutcomesFormatsProbability(double probability, String expected) {
    assertEquals("a[" + expected + "]", model("a").getAllOutcomes(new double[] {probability}));
  }

  @Test
  void testGetAllOutcomesJoinsOutcomes() {
    assertEquals("a[0.2500]  b[0.7500]",
        model("a", "b").getAllOutcomes(new double[] {0.25, 0.75}));
  }

  @Test
  void testGetAllOutcomesRejectsForeignArray() {
    String result = model("a", "b").getAllOutcomes(new double[] {1.0});
    assertTrue(result.startsWith("The double array sent as a parameter"));
  }

  @Test
  void testGetAllOutcomesConcurrently() throws Exception {
    AbstractModel shared = model("a", "b", "c");
    double[] probs = {1.0 / 3, 0.09375, 0.5732198};
    String expected = "a[0.3333]  b[0.0938]  c[0.5732]";

    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    try {
      List<Callable<Integer>> tasks = new ArrayList<>();
      for (int t = 0; t < THREADS; t++) {
        tasks.add(() -> {
          int mismatches = 0;
          for (int i = 0; i < ITERATIONS; i++) {
            if (!expected.equals(shared.getAllOutcomes(probs))) {
              mismatches++;
            }
          }
          return mismatches;
        });
      }
      for (Future<Integer> future : executor.invokeAll(tasks)) {
        assertEquals(0, future.get());
      }
    } finally {
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
    }
  }
}
