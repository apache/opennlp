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
package opennlp.embeddings;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The distiller's pure pieces: the Zipf weighting matches Model2Vec's formula
 * ({@code sif / (sif + p)}, {@code p} the row's share of a Zipf distribution), and the
 * safetensors writer's output round-trips through the module's reader.
 */
class ModelDistillerTest {

  @Test
  void testZipfWeightsFollowTheModel2vecFormula() {
    // Two rows: the Zipf distribution is over 1/2 and 1/3, normalized by their sum 5/6.
    final float[] weights = ModelDistiller.zipfWeights(2, 1e-4);

    assertEquals(2, weights.length);
    assertEquals(1e-4 / (1e-4 + 0.6), weights[0], 1e-10);
    assertEquals(1e-4 / (1e-4 + 0.4), weights[1], 1e-10);
  }

  @Test
  void testZipfWeightsDiscountEarlyRows() {
    final float[] weights = ModelDistiller.zipfWeights(1000, 1e-4);

    // Frequent (early) tokens are down-weighted relative to rare (late) ones.
    for (int i = 1; i < weights.length; i++) {
      assert weights[i] > weights[i - 1];
    }
    double harmonicSum = 0;
    for (int j = 2; j <= 1001; j++) {
      harmonicSum += 1.0 / j;
    }
    assertEquals(1e-4 / (1e-4 + 1.0 / 1001 / harmonicSum), weights[weights.length - 1], 1e-5);
  }

  @Test
  void testSafetensorsWriterRoundTripsThroughTheReader(@TempDir Path dir) throws IOException {
    final float[] values = {1.5f, -2.25f, 3e8f, 0, -0.5f, 42};
    final Path file = dir.resolve("model.safetensors");

    SafetensorsWriter.writeMatrix(file, 2, 3, values);

    final SafetensorsFile tensors = SafetensorsFile.read(file);
    assertEquals(SafetensorsWriter.EMBEDDINGS_TENSOR, tensors.singleMatrixTensorName());
    assertArrayEquals(new int[] {2, 3}, tensors.tensorInfo(SafetensorsWriter.EMBEDDINGS_TENSOR)
        .shape());
    assertArrayEquals(values, tensors.readFloats(SafetensorsWriter.EMBEDDINGS_TENSOR));
  }
}
