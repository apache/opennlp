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

import java.io.File;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import ai.onnxruntime.OrtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.dl.InferenceOptions;
import opennlp.tools.embeddings.EmbeddingException;

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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@link SentenceVectorsDL} reuses its direct buffers between inferences, on the tiny
 * models with a hidden dimension of 3 and on {@code tiny-wide.onnx}, whose hidden dimension of
 * 1024 is not declared in the model metadata.
 */
class SentenceVectorsDLDirectMemoryTest {

  private static final String TINY_WIDE = "tiny-wide.onnx";

  private static final String TINY_FIXED = "tiny-fixed.onnx";

  /** {@code tiny-fixed.onnx} accepts inputs of exactly this many tokens. */
  private static final int FIXED_TOKENS = 8;

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

  /** Checks that the buffer size of an inference is computed without overflowing {@code int}. */
  @Test
  void bufferBytesAreComputedInLong() {
    // 3 inputs of 2 * 2 longs and 2 pooled vectors of 3 floats.
    assertEquals(3 * (2 * 2) * Long.BYTES + (2 * 3) * Float.BYTES,
        SentenceVectorsDL.bufferBytes(2, 2, 3, true));
    // 3 inputs of 2 * 2 longs and 2 * 2 token vectors of 3 floats.
    assertEquals(3 * (2 * 2) * Long.BYTES + (2 * 2 * 3) * Float.BYTES,
        SentenceVectorsDL.bufferBytes(2, 2, 3, false));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> SentenceVectorsDL.bufferBytes(1, Integer.MAX_VALUE, 3, true));
    assertTrue(e.getMessage().startsWith("The inference needs "), e.getMessage());
  }

  /**
   * Checks that buffer-size arithmetic reports an overlong inference instead of overflowing
   * {@code long} for large row counts, lengths and dimensions.
   */
  @ParameterizedTest
  @CsvSource({
      "2, 2147483647, 2147483647, false",
      "1, 2147483647, 1073741824, false",
      "1, 1073741824, 2147483647, true",
      "2147483647, 2147483647, 2147483647, false"})
  void bufferBytesRejectExtremeSizesWithoutOverflowing(final int rows, final int length,
      final int dimension, final boolean pooledOutput) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> SentenceVectorsDL.bufferBytes(rows, length, dimension, pooledOutput));
    assertTrue(e.getMessage().startsWith("The inference needs "), e.getMessage());
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

  /**
   * Checks that threads beyond the concurrency limit wait for a running inference instead of
   * allocating a direct buffer of their own, so the instance allocates no more buffers than the
   * limit.
   */
  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3})
  void concurrentCallsAllocateNoMoreBuffersThanTheLimit(final int limit, @TempDir final Path dir)
      throws Exception {
    final InferenceOptions options = new InferenceOptions();
    options.setMaxConcurrentInferences(limit);
    final ExecutorService executor = Executors.newFixedThreadPool(8);
    try (SentenceVectorsDL vectors = new SentenceVectorsDL(model(dir, TINY_VECTORS), vocab(dir),
        true, new SentenceVectorsOptions(Pooling.MEAN, false,
            SentenceVectorsDL.DEFAULT_MAX_LENGTH, PaddingStrategy.EXACT_LENGTH), options)) {
      // One tokenized length, so a buffer never has to grow and every allocation is a new one.
      final List<String> texts = Collections.nCopies(64, HELLO_WORLD);
      final List<Future<?>> futures = new ArrayList<>();
      for (int t = 0; t < 8; t++) {
        futures.add(executor.submit(() -> {
          for (int i = 0; i < 50; i++) {
            vectors.embedAll(texts);
          }
          return null;
        }));
      }
      for (final Future<?> future : futures) {
        future.get();
      }
      assertTrue(vectors.allocatedBuffers() <= limit,
          "allocated " + vectors.allocatedBuffers() + " buffers for a limit of " + limit);
    } finally {
      executor.shutdownNow();
    }
  }

  /** Checks that growing one pool slot preserves the other callers' reusable buffers. */
  @ParameterizedTest
  @ValueSource(ints = {2, 3})
  @SuppressWarnings("unchecked")
  void mixedLengthsGrowOnlyOneBufferAtATime(final int limit, @TempDir final Path dir)
      throws Exception {
    final InferenceOptions options = new InferenceOptions();
    options.setMaxConcurrentInferences(limit);
    try (SentenceVectorsDL vectors = new SentenceVectorsDL(model(dir, TINY_VECTORS), vocab(dir),
        true, new SentenceVectorsOptions(Pooling.MEAN, false,
            SentenceVectorsDL.DEFAULT_MAX_LENGTH, PaddingStrategy.EXACT_LENGTH), options)) {
      // Seed idle slots deterministically rather than relying on native inferences overlapping.
      final Field field = SentenceVectorsDL.class.getDeclaredField("spareBuffers");
      field.setAccessible(true);
      final BlockingQueue<ByteBuffer> buffers = (BlockingQueue<ByteBuffer>) field.get(vectors);
      for (int i = 0; i < limit; i++) {
        buffers.add(ByteBuffer.allocateDirect(SentenceVectorsDL.bufferBytes(1, 3 + i, 3, false)));
      }
      final List<String> large = Collections.nCopies(64, HELLO_WORLD);
      for (int i = 0; i < 10; i++) {
        for (final float[] vector : vectors.embedAll(large)) {
          assertArrayEquals(HELLO_WORLD_MEAN, vector, DELTA);
        }
        assertEquals(limit, buffers.size(), "growing one slot must preserve the other slots");
        assertArrayEquals(HELLO_MEAN, vectors.embed(HELLO), DELTA);
        assertArrayEquals(HELLO_WORLD_MEAN, vectors.embed(HELLO_WORLD), DELTA);
      }
      assertTrue(vectors.allocatedBuffers() <= limit,
          "each seeded slot should grow at most once for this workload");
    }
  }

  /** Checks that executor shutdown cancels a queued inference without losing a permit. */
  @Test
  void shutdownInterruptsAnInferenceWaitingForAPermit(@TempDir final Path dir) throws Exception {
    final InferenceOptions options = new InferenceOptions();
    options.setMaxConcurrentInferences(1);
    try (SentenceVectorsDL vectors = new SentenceVectorsDL(model(dir, TINY_VECTORS), vocab(dir),
        true, new SentenceVectorsOptions(Pooling.MEAN, false,
            SentenceVectorsDL.DEFAULT_MAX_LENGTH, PaddingStrategy.EXACT_LENGTH), options)) {
      // Hold the permit so the worker waits without depending on native inference timing.
      final Field field = SentenceVectorsDL.class.getDeclaredField("inferencePermits");
      field.setAccessible(true);
      final Semaphore permits = (Semaphore) field.get(vectors);
      permits.acquire();
      final ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
        final Future<?> waiting = executor.submit(() -> {
          final EmbeddingException failure = assertThrows(EmbeddingException.class,
              () -> vectors.embed(HELLO));
          assertInstanceOf(InterruptedException.class, failure.getCause());
          assertTrue(Thread.currentThread().isInterrupted(), "interrupt flag must be restored");
        });
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
          while (!permits.hasQueuedThreads()) {
            Thread.sleep(1);
          }
        });
        executor.shutdownNow();
        waiting.get(5, TimeUnit.SECONDS);
        assertEquals(0, permits.availablePermits(), "a cancelled waiter must not release a permit");
        assertEquals(0, vectors.allocatedBuffers(), "a cancelled waiter must not run inference");
      } finally {
        permits.release();
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
      }
      assertArrayEquals(HELLO_MEAN, vectors.embed(HELLO), DELTA);
      assertEquals(1, permits.availablePermits(), "subsequent inference must return its permit");
    }
  }

  /** Checks that a negative concurrency limit is rejected. */
  @Test
  void negativeConcurrencyLimitIsRejected(@TempDir final Path dir) throws Exception {
    final File model = model(dir, TINY_VECTORS);
    final File vocab = vocab(dir);
    final InferenceOptions options = new InferenceOptions();
    options.setMaxConcurrentInferences(-1);
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new SentenceVectorsDL(model, vocab, true, new SentenceVectorsOptions(Pooling.MEAN,
            false, SentenceVectorsDL.DEFAULT_MAX_LENGTH, PaddingStrategy.EXACT_LENGTH),
            options).close());
    assertEquals("maxConcurrentInferences must not be negative", e.getMessage());
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
   * Checks that the probe for an undeclared dimension runs at the width of
   * {@link PaddingStrategy#MAX_LENGTH}. {@code tiny-fixed.onnx} multiplies against a table of 8
   * tokens and accepts no other width, and its output width is the token count, so the probe
   * measures a dimension of 8 at a maximum length of 8 and fails at 9.
   */
  @Test
  void probesAnUndeclaredDimensionAtMaxLength(@TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL vectors = new SentenceVectorsDL(model(dir, TINY_FIXED), vocab(dir),
        true, new SentenceVectorsOptions(Pooling.MEAN, false, FIXED_TOKENS,
            PaddingStrategy.MAX_LENGTH), new InferenceOptions())) {
      assertEquals(FIXED_TOKENS, vectors.dimension());
    }
    final SentenceVectorsOptions tooLong = new SentenceVectorsOptions(Pooling.MEAN, false,
        FIXED_TOKENS + 1, PaddingStrategy.MAX_LENGTH);
    assertThrows(OrtException.class, () -> new SentenceVectorsDL(model(dir, TINY_FIXED),
        vocab(dir), true, tooLong, new InferenceOptions()).close());
  }

  /**
   * Checks that threads sharing one instance, and so the buffers of its pool, each get the
   * vectors of their own texts, with calls of different sizes.
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
