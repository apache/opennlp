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
package opennlp.embeddings.eval;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.embeddings.index.FlatFloatIndex;
import opennlp.embeddings.index.VectorIndex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks build timing with a controlled clock and a float index. */
class IndexBuildTest {

  private static final String BUILD_FAILURE_MESSAGE = "build failed";
  private static final String CLOSE_FAILURE_MESSAGE = "close failed";

  @ParameterizedTest
  @CsvSource({"1,0,0", "0,1,0", "0,0,1", "2,3,5"})
  void testIncludesConstructionInsertionAndFreeze(long createMillis, long addMillis,
                                                  long freezeMillis) {
    final AtomicLong clock = new AtomicLong();

    final IndexBuild<ClockedIndex> build = IndexBuild.measure(
        () -> new ClockedIndex(clock, createMillis, addMillis, freezeMillis), index -> {
          index.add("a", new float[] {1, 0});
          index.add("b", new float[] {0, 1});
        }, clock::get);

    assertEquals(createMillis + 2 * addMillis + freezeMillis, build.millis());
    assertEquals(2, build.index().size());
    assertEquals("a", build.index().topK(new float[] {1, 0}, 1).getFirst().id());
    assertFalse(build.index().closed);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testClosesIndexAfterBuildFailure(boolean duringFreeze) {
    final AtomicLong clock = new AtomicLong();
    final ClockedIndex index = new ClockedIndex(clock, 0, 0, 0);
    final IllegalStateException failure = new IllegalStateException(BUILD_FAILURE_MESSAGE);
    if (duringFreeze) {
      index.freezeFailure = failure;
    }

    final IllegalStateException actual = assertThrows(IllegalStateException.class,
        () -> IndexBuild.measure(() -> index, target -> {
          if (!duringFreeze) {
            throw failure;
          }
          target.add("a", new float[] {1, 0});
        }, clock::get));

    assertSame(failure, actual);
    assertTrue(index.closed);
  }

  @Test
  void testRetainsBuildFailureWhenCloseFails() {
    final AtomicLong clock = new AtomicLong();
    final ClockedIndex index = new ClockedIndex(clock, 0, 0, 0);
    final IllegalStateException failure = new IllegalStateException(BUILD_FAILURE_MESSAGE);
    index.closeFailure = new IOException(CLOSE_FAILURE_MESSAGE);

    final IllegalStateException actual = assertThrows(IllegalStateException.class,
        () -> IndexBuild.measure(() -> index, target -> {
          throw failure;
        }, clock::get));

    assertSame(failure, actual);
    assertEquals(1, actual.getSuppressed().length);
    assertSame(index.closeFailure, actual.getSuppressed()[0]);
    assertTrue(index.closed);
  }

  @Test
  void testRetainsBuildFailureWhenCloseThrowsError() {
    final AtomicLong clock = new AtomicLong();
    final ClockedIndex index = new ClockedIndex(clock, 0, 0, 0);
    final IllegalStateException failure = new IllegalStateException(BUILD_FAILURE_MESSAGE);
    index.closeError = new AssertionError(CLOSE_FAILURE_MESSAGE);

    final IllegalStateException actual = assertThrows(IllegalStateException.class,
        () -> IndexBuild.measure(() -> index, target -> {
          throw failure;
        }, clock::get));

    assertSame(failure, actual);
    assertEquals(1, actual.getSuppressed().length);
    assertSame(index.closeError, actual.getSuppressed()[0]);
    assertTrue(index.closed);
  }

  @Test
  void testFactoryFailureDoesNotPopulateAnIndex() {
    final IllegalStateException failure = new IllegalStateException("construction failed");
    final AtomicBoolean populated = new AtomicBoolean();

    final IllegalStateException actual = assertThrows(IllegalStateException.class,
        () -> IndexBuild.measure(() -> {
          throw failure;
        }, index -> populated.set(true), () -> 0L));

    assertSame(failure, actual);
    assertFalse(populated.get());
  }

  /** A float index that advances a clock during build operations. */
  private static final class ClockedIndex implements VectorIndex, AutoCloseable {

    private final FlatFloatIndex delegate = new FlatFloatIndex(2);
    private final AtomicLong clock;
    private final long addNanos;
    private final long freezeNanos;
    private boolean closed;
    private RuntimeException freezeFailure;
    private IOException closeFailure;
    private Error closeError;

    /**
     * Assigns clock costs to construction, insertion and freezing.
     *
     * @param clock The controlled nanosecond clock.
     * @param createMillis The construction cost.
     * @param addMillis The cost per vector insertion.
     * @param freezeMillis The freezing cost.
     */
    private ClockedIndex(AtomicLong clock, long createMillis, long addMillis, long freezeMillis) {
      this.clock = clock;
      this.addNanos = TimeUnit.MILLISECONDS.toNanos(addMillis);
      this.freezeNanos = TimeUnit.MILLISECONDS.toNanos(freezeMillis);
      clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(createMillis));
    }

    /** {@inheritDoc} */
    @Override
    public void add(String id, float[] vector) {
      clock.addAndGet(addNanos);
      delegate.add(id, vector);
    }

    /** {@inheritDoc} */
    @Override
    public void freeze() {
      clock.addAndGet(freezeNanos);
      if (freezeFailure != null) {
        throw freezeFailure;
      }
      delegate.freeze();
    }

    /** {@inheritDoc} */
    @Override
    public List<Hit> topK(float[] query, int k) {
      return delegate.topK(query, k);
    }

    /** {@inheritDoc} */
    @Override
    public int size() {
      return delegate.size();
    }

    /** {@inheritDoc} */
    @Override
    public int dimension() {
      return delegate.dimension();
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws IOException {
      closed = true;
      if (closeError != null) {
        throw closeError;
      }
      if (closeFailure != null) {
        throw closeFailure;
      }
    }
  }
}
