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

import java.util.List;

/**
 * The interface for place containment hierarchies: given a place identifier, report the
 * chain of enclosing places, so a mention expands into the places it belongs to.
 *
 * @see PlaceAncestor
 * @since 3.0.0
 */
public interface PlaceHierarchy {

  /**
   * Walks the containment chain of a place.
   *
   * @param id The place identifier in this hierarchy's identifier space. Must not be
   *           {@code null}.
   * @return The enclosing places from the nearest outward, excluding the place itself.
   *         Never {@code null}; empty when the identifier is unknown or the place has
   *         no recorded parent.
   * @throws IllegalArgumentException Thrown if {@code id} is {@code null}.
   */
  List<PlaceAncestor> ancestors(String id);
}
