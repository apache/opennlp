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

package opennlp.tools.depparse;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

import opennlp.tools.commons.ThreadSafe;

/**
 * Caches the hidden-layer contribution of each (feature position, embedding row) pair
 * a {@link FeedforwardDependencyParser} scores, within a byte budget.
 *
 * <p>Each position has one table sized to the vocabulary block it reads, so a tag or
 * label position never reserves a slot per word. The tables are charged to the budget
 * up front; contribution vectors are charged when they are computed. Pairs that do
 * not fit are scored directly. Contributions keep double precision, so cached and
 * direct scoring return the same values.</p>
 *
 * @since 3.0.0
 */
@ThreadSafe
final class ContributionCache {

  /** The bytes charged per table slot. */
  static final long REFERENCE_BYTES = 8L;

  /** The bytes charged per contribution vector beyond its values. */
  static final long ARRAY_HEADER_BYTES = 16L;

  /** The bytes of one double value. */
  private static final long DOUBLE_BYTES = 8L;

  private final FeedforwardNetwork network;

  /** The cached contribution vectors, indexed by feature position and row offset. */
  private final AtomicReferenceArray<double[]>[] byPosition;

  /** The first embedding row of the vocabulary block each position reads. */
  private final int[] firstRow;

  /** The bytes one contribution vector costs. */
  private final long entryBytes;

  /** The bytes the cache may still spend. */
  private final AtomicLong remainingBytes;

  /** The number of contribution vectors published so far. */
  private final AtomicLong entries = new AtomicLong();

  /**
   * Allocates the tables of a cache.
   *
   * @param network The network whose contributions are cached. Must not be
   *                {@code null}.
   * @param budgetBytes The bytes the tables and the contribution vectors may use
   *                    together.
   * @throws IllegalArgumentException Thrown if the tables alone exceed
   *         {@code budgetBytes}.
   */
  @SuppressWarnings("unchecked")
  ContributionCache(FeedforwardNetwork network, long budgetBytes) {
    this.network = network;
    this.firstRow = new int[FeedforwardContext.FEATURE_COUNT];
    final int[] rows = new int[FeedforwardContext.FEATURE_COUNT];
    long tableBytes = 0L;
    for (int f = 0; f < FeedforwardContext.FEATURE_COUNT; f++) {
      if (f < FeedforwardContext.POSITIONS) {
        firstRow[f] = network.firstWordRow();
        rows[f] = network.wordIds().size();
      } else if (f < 2 * FeedforwardContext.POSITIONS) {
        firstRow[f] = network.firstTagRow();
        rows[f] = network.tagIds().size();
      } else {
        firstRow[f] = network.firstLabelRow();
        rows[f] = network.labelIds().size();
      }
      tableBytes += ARRAY_HEADER_BYTES + REFERENCE_BYTES * rows[f];
    }
    if (tableBytes > budgetBytes) {
      throw new IllegalArgumentException("scoringCacheBytes must be at least " + tableBytes
          + " for the vocabularies of this model: " + budgetBytes);
    }
    this.byPosition = new AtomicReferenceArray[FeedforwardContext.FEATURE_COUNT];
    for (int f = 0; f < byPosition.length; f++) {
      byPosition[f] = new AtomicReferenceArray<>(rows[f]);
    }
    this.entryBytes = ARRAY_HEADER_BYTES + DOUBLE_BYTES * network.hiddenSize();
    this.remainingBytes = new AtomicLong(budgetBytes - tableBytes);
  }

  /**
   * Returns the cached hidden-layer contribution of one pair, computing and
   * publishing it on first sight while the budget lasts.
   *
   * @param position The feature position.
   * @param row The embedding row at that position.
   * @return The contribution vector, or {@code null} if the pair is not cached and the
   *         budget is spent.
   */
  double[] contribution(int position, int row) {
    final AtomicReferenceArray<double[]> slots = byPosition[position];
    final int slot = row - firstRow[position];
    if (slot < 0 || slot >= slots.length()) {
      return null;
    }
    double[] contribution = slots.get(slot);
    if (contribution != null) {
      return contribution;
    }
    if (!reserve()) {
      return null;
    }
    contribution = new double[network.hiddenSize()];
    network.addContribution(position, row, contribution);
    if (slots.compareAndSet(slot, null, contribution)) {
      entries.incrementAndGet();
      return contribution;
    }
    remainingBytes.addAndGet(entryBytes);
    return slots.get(slot);
  }

  /**
   * Reserves the bytes of one contribution vector.
   *
   * @return Whether the vector fits the remaining budget.
   */
  private boolean reserve() {
    long available = remainingBytes.get();
    while (available >= entryBytes) {
      if (remainingBytes.compareAndSet(available, available - entryBytes)) {
        return true;
      }
      available = remainingBytes.get();
    }
    return false;
  }

  /**
   * @return The number of contribution vectors the cache holds.
   */
  long entries() {
    return entries.get();
  }

  /**
   * @return The bytes the cache may still spend on contribution vectors.
   */
  long remainingBytes() {
    return remainingBytes.get();
  }

  /**
   * @return The bytes one contribution vector costs.
   */
  long entryBytes() {
    return entryBytes;
  }
}
