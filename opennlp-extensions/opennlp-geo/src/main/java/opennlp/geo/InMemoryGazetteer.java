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

package opennlp.geo;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.geo.Gazetteer;
import opennlp.tools.geo.GazetteerEntry;

/**
 * A {@link Gazetteer} over caller-supplied entries held in memory, indexed and ranked
 * exactly like the bundled table (same folding chain, same candidate order), so place
 * records from any origin, for example a database or a remote service, get the matching
 * behavior of {@link BundledGazetteer} without a file format in between.
 *
 * <p>The bundled table is never loaded and the shared
 * {@link BundledGazetteer#getInstance()} instance is not affected, so callers can build
 * any number of independent gazetteers over their own place records.</p>
 *
 * <p>Instances are immutable and safe to share between threads.</p>
 */
@ThreadSafe
public final class InMemoryGazetteer implements Gazetteer {

  // Shares the bundled table's index implementation over the entries given here only.
  private final BundledGazetteer index;

  private InMemoryGazetteer(List<GazetteerEntry> entries) {
    this.index = new BundledGazetteer(entries);
  }

  /**
   * Creates a gazetteer over the given entries.
   *
   * @param entries The entries to index. Must not be {@code null} or contain {@code null}
   *                elements.
   * @return A new immutable, thread-safe gazetteer over the given entries.
   * @throws IllegalArgumentException Thrown if {@code entries} is {@code null}, contains a
   *     {@code null} element, contains two entries with the same (source, recordId), or
   *     contains an entry with a name that folds to an empty match key.
   */
  public static InMemoryGazetteer fromEntries(List<GazetteerEntry> entries) {
    return new InMemoryGazetteer(entries);
  }

  /** {@inheritDoc} */
  @Override
  public List<GazetteerEntry> lookup(CharSequence name) {
    return index.lookup(name);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<GazetteerEntry> byId(String source, String recordId) {
    return index.byId(source, recordId);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<GazetteerEntry> byRegion(String isoCountryCode) {
    return index.byRegion(isoCountryCode);
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> sources() {
    return index.sources();
  }
}
