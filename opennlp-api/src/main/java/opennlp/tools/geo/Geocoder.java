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
package opennlp.tools.geo;

import java.io.IOException;
import java.util.List;

import opennlp.tools.util.Span;

/**
 * Resolves location mentions in a document against a {@link Gazetteer}.
 *
 * <p>The mentions are entity spans in ORIGINAL document coordinates, typically the
 * {@code LOCATION} spans produced by a name finder. The whole document text is passed alongside
 * them so an implementation can use co-occurring mentions for disambiguation; a simple
 * implementation may score each mention independently, and a context-aware scorer is a later
 * implementation of this same contract, not a contract change.</p>
 *
 * <p>Relationship to {@link opennlp.tools.entitylinker.EntityLinker}: that interface is the
 * generic entity enrichment contract for any entity type. New consumers that resolve location
 * mentions against place data should target this interface and {@link Gazetteer};
 * {@code EntityLinker} remains the contract for generic entity enrichment beyond locations.</p>
 *
 * <p>Implementations must be immutable and thread-safe after construction, so one geocoder can
 * serve concurrent documents.</p>
 */
public interface Geocoder {

  /**
   * Resolves the given location mentions against this geocoder's gazetteer.
   *
   * <p>The results are aligned to the input: each returned {@link GeoResolution#mention()} is one
   * of the input spans, and results appear in input order. A mention the geocoder cannot resolve
   * is omitted, so the result list may be shorter than the input list; a resolution is never
   * fabricated for an unresolvable mention.</p>
   *
   * @param text             The whole document text the spans index into. Must not be
   *                         {@code null}.
   * @param locationMentions The location mention spans, in original document coordinates. Must
   *                         not be {@code null} or contain {@code null} elements, and every span
   *                         must lie within {@code text}.
   * @return The resolutions for the resolvable mentions, in input order; never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code text} or {@code locationMentions} is
   *     {@code null}, or a mention is {@code null} or out of the text's bounds.
   * @throws IOException Thrown if the underlying {@link Gazetteer} fails with it; a geocoder
   *     over an in-memory gazetteer never throws it.
   */
  List<GeoResolution> resolve(CharSequence text, List<Span> locationMentions) throws IOException;
}
