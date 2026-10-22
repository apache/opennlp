/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.dl.vectors;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static opennlp.dl.vectors.TinyVectorModels.DELTA;
import static opennlp.dl.vectors.TinyVectorModels.HELLO;
import static opennlp.dl.vectors.TinyVectorModels.HELLO_MEAN;
import static opennlp.dl.vectors.TinyVectorModels.HELLO_WORLD;
import static opennlp.dl.vectors.TinyVectorModels.HELLO_WORLD_MEAN;
import static opennlp.dl.vectors.TinyVectorModels.TINY_POOLED;
import static opennlp.dl.vectors.TinyVectorModels.TINY_VECTORS;
import static opennlp.dl.vectors.TinyVectorModels.alternating;
import static opennlp.dl.vectors.TinyVectorModels.model;
import static opennlp.dl.vectors.TinyVectorModels.vocab;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@link SentenceVectorsDL} reuses its direct buffers between inferences, on the tiny
 * models with a hidden dimension of 3 and on {@code tiny-wide.onnx}, whose hidden dimension of
 * 1024 is not declared in the model metadata.
 */
class SentenceVectorsDLDirectMemoryTest {

  private static final String TINY_WIDE = "tiny-wide.onnx";

  private static final int WIDE = 1024;

  /**
   * {@return the number of direct buffers the JVM holds}
   */
  private static long directBuffers() {
    for (final BufferPoolMXBean pool
        : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
      if ("direct".equals(pool.getName())) {
        return pool.getCount();
      }
    }
    throw new IllegalStateException("The JVM reports no direct buffer pool.");
  }

  /**
   * {@return the expected vector of {@code tiny-wide.onnx} for a mean token id}
   *
   * @param mean The mean token id.
   */
  private static float[] wide(final float mean) {
    final float[] vector = new float[WIDE];
    for (int d = 0; d < WIDE; d++) {
      vector[d] = (d % 5 - 2) / 2f * mean;
    }
    return vector;
  }

  /**
   * Embeds once to size the buffers, then embeds the same and smaller calls again.
   *
   * @param vectors The session.
   */
  private static void embedRepeatedly(final SentenceVectorsDL vectors) {
    final List<String> texts = alternating(64);
    vectors.embedAll(texts);
    for (int i = 0; i < 20; i++) {
      vectors.embedAll(texts);
      vectors.embed(HELLO);
      vectors.embed(HELLO_WORLD);
    }
  }

  /**
   * Checks that calls no larger than an earlier one allocate no direct buffer, for token and
   * pooled outputs and for a hidden dimension of 3 and of 1024.
   */
  @ParameterizedTest
  @ValueSource(strings = {TINY_VECTORS, TINY_POOLED, TINY_WIDE})
  void repeatedCallsAllocateNoDirectBuffers(final String name, @TempDir final Path dir)
      throws Exception {
    try (SentenceVectorsDL vectors = new SentenceVectorsDL(model(dir, name), vocab(dir), true,
        Pooling.MEAN, false, SentenceVectorsDL.DEFAULT_MAX_LENGTH)) {
      embedRepeatedly(vectors);
      final long before = directBuffers();
      embedRepeatedly(vectors);
      // A collection can only lower the count, so any growth is a buffer allocated per call.
      assertTrue(directBuffers() <= before,
          "direct buffers grew from " + before + " to " + directBuffers());
    }
  }

  /** A model that does not declare its hidden dimension gets it measured, and pools with it. */
  @Test
  void measuresAnUndeclaredDimension(@TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL vectors = new SentenceVectorsDL(model(dir, TINY_WIDE), vocab(dir),
        true, Pooling.MEAN, false, SentenceVectorsDL.DEFAULT_MAX_LENGTH)) {
      assertEquals(WIDE, vectors.dimension());
      assertArrayEquals(wide(19 / 4f), vectors.embed(HELLO_WORLD), DELTA);
      final float[][] all = vectors.embedAll(alternating(3));
      for (int i = 0; i < all.length; i++) {
        assertArrayEquals(i % 2 == 0 ? wide(19 / 4f) : wide(14 / 3f), all[i], DELTA);
      }
    }
  }

  /**
   * Checks that threads sharing one instance, and so its spare buffer, each get the vectors of
   * their own texts, with calls of different sizes.
   */
  @ParameterizedTest
  @ValueSource(strings = {TINY_VECTORS, TINY_WIDE})
  void concurrentCallsGetTheirOwnVectors(final String name, @TempDir final Path dir)
      throws Exception {
    final float[] helloWorld = TINY_WIDE.equals(name) ? wide(19 / 4f) : HELLO_WORLD_MEAN;
    final float[] hello = TINY_WIDE.equals(name) ? wide(14 / 3f) : HELLO_MEAN;
    final ExecutorService executor = Executors.newFixedThreadPool(8);
    try (SentenceVectorsDL vectors = new SentenceVectorsDL(model(dir, name), vocab(dir), true,
        Pooling.MEAN, false, SentenceVectorsDL.DEFAULT_MAX_LENGTH)) {
      final List<Future<?>> futures = new ArrayList<>();
      for (int t = 0; t < 8; t++) {
        final int pairs = 1 + t * 7;
        futures.add(executor.submit(() -> {
          for (int i = 0; i < 50; i++) {
            final float[][] all = vectors.embedAll(alternating(pairs));
            for (int r = 0; r < all.length; r++) {
              assertArrayEquals(r % 2 == 0 ? helloWorld : hello, all[r], DELTA);
            }
            assertArrayEquals(hello, vectors.embed(HELLO), DELTA);
          }
          return null;
        }));
      }
      for (final Future<?> future : futures) {
        future.get();
      }
    } finally {
      executor.shutdownNow();
    }
  }
}
