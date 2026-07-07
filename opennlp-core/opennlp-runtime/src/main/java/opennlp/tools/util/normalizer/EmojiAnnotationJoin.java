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
package opennlp.tools.util.normalizer;

import java.util.Map;

/**
 * The joined-facts layer of the emoji annotation record store: a hook through which facts that
 * live in user-installed data, typically a gazetteer (coordinates, containment chain, GeoNames or
 * Who's On First identifiers), are resolved at run time and merged into an
 * {@link EmojiAnnotation}. Register an implementation on an
 * {@link EmojiAnnotator#EmojiAnnotator(EmojiAnnotationJoin) EmojiAnnotator}; no implementation is
 * shipped.
 *
 * <p>Foreign identifiers are never baked into the bundled data file, by design: they get
 * deprecated and superseded upstream (identifier churn), the datasets they point into are
 * optional downloads, and baking them in would couple the bundled table's version to the
 * dataset's version. The stable join key is the ISO 3166 code decoded by {@link EmojiFlags}, so
 * a flag emoji resolves against whatever region data the user has installed. Checking that a
 * decoded code is an <em>assigned</em> region also happens here, not in bundled or derived
 * data.</p>
 */
@FunctionalInterface
public interface EmojiAnnotationJoin {

  /**
   * Resolves joined attribute values for one symbol. Called by {@link EmojiAnnotator} only while
   * an annotation is being assembled, that is when the symbol already has bundled or derived
   * facts; the join augments records, it never creates them.
   *
   * @param symbol    The annotated code point sequence, without the U+FE0F presentation selector.
   * @param isoRegion The ISO 3166 code decoded from the symbol, or {@code null} when the symbol
   *                  is not a flag.
   * @return The joined attribute values keyed by attribute name, empty when there is nothing to
   *     add; never {@code null}. Keys must not collide with attributes already on the record
   *     (the annotator fails loud on a collision), and every value carries its own provenance in
   *     {@link EmojiAnnotation.Value#source()}.
   */
  Map<String, EmojiAnnotation.Value> join(String symbol, String isoRegion);
}
