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

/**
 * The coarse entity type of an annotated emoji, a small project-authored taxonomy assigned from
 * the subgroup headers of the upstream Unicode {@code emoji-test.txt} (Emoji Keyboard/Display Test
 * Data for <a href="https://www.unicode.org/reports/tr51/">UTS&#160;#51</a>): for example the
 * {@code face-smiling} and {@code face-concerned} subgroups map to {@link #FACE} and
 * {@code transport-ground} maps to {@link #VEHICLE}. The subgroup a bundled record was derived
 * from is cited in the notes column of {@code emoji-annotations.txt}, provenance-tagged
 * {@code UCD:emoji-test}.
 *
 * <p>The set is deliberately coarse and grows with the bundled data; a new constant is a
 * compatible addition. {@link #FLAG} is assigned by the derived layer (regional-indicator and tag
 * sequence decoding), never by a bundled row, so flag coverage does not depend on data
 * rows.</p>
 *
 * @see EmojiCategory
 */
public enum EmojiEntityType {

  /** A face pictograph, from the {@code face-*} subgroups. */
  FACE,

  /** A heart pictograph, from the {@code heart} subgroup. */
  HEART,

  /** An animal, from the {@code animal-*} subgroups. */
  ANIMAL,

  /** Food, from the {@code food-*} subgroups. */
  FOOD,

  /** A vehicle, from the {@code transport-*} subgroups. */
  VEHICLE,

  /** A landmark or building, from the {@code place-*} subgroups. */
  LANDMARK,

  /** A flag; assigned by the derived layer from the code point sequence itself. */
  FLAG
}
