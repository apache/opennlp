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

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.util.StringUtil;

/**
 * One step of a place's containment chain: an enclosing place with its identifier in
 * the hierarchy source, its name, and its place type.
 *
 * @param id The identifier in the hierarchy source. Must not be {@code null} or blank.
 * @param name The place name. Must not be {@code null} or blank.
 * @param type The place type, for example {@code borough} or {@code country}. Must not
 *             be {@code null} or blank.
 */
@ThreadSafe
public record PlaceAncestor(String id, String name, String type) {

  /**
   * Validates that every component of the ancestor is present and non-blank.
   *
   * @throws IllegalArgumentException Thrown if a component is {@code null} or blank.
   */
  public PlaceAncestor {
    if (id == null || StringUtil.isBlank(id)) {
      throw new IllegalArgumentException("id must not be null or blank");
    }
    if (name == null || StringUtil.isBlank(name)) {
      throw new IllegalArgumentException("name must not be null or blank");
    }
    if (type == null || StringUtil.isBlank(type)) {
      throw new IllegalArgumentException("type must not be null or blank");
    }
  }
}
