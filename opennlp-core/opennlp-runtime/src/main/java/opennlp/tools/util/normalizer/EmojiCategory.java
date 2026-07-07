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
 * The document-category hint of an annotated emoji, one constant per group header of the upstream
 * Unicode {@code emoji-test.txt} (Emoji Keyboard/Display Test Data for
 * <a href="https://www.unicode.org/reports/tr51/">UTS&#160;#51</a>). The group level of that file
 * is already a document-category-shaped taxonomy, so this enum mirrors it one to one rather than
 * inventing a competing one; the constant a bundled record carries is provenance-tagged
 * {@code UCD:emoji-test} in {@code emoji-annotations.txt}.
 *
 * <p>All upstream groups are represented, including those the bundled data does not populate yet,
 * so growing the data never needs an enum change. See {@link EmojiEntityType} for the finer,
 * subgroup-derived projection of the same upstream table.</p>
 */
public enum EmojiCategory {

  /** Upstream group {@code Smileys & Emotion}: faces, hearts, and other emotion symbols. */
  SMILEYS_AND_EMOTION,

  /** Upstream group {@code People & Body}: people, body parts, hand gestures, roles. */
  PEOPLE_AND_BODY,

  /** Upstream group {@code Component}: skin tone and hair components, not free-standing symbols. */
  COMPONENT,

  /** Upstream group {@code Animals & Nature}: animals, plants, and nature symbols. */
  ANIMALS_AND_NATURE,

  /** Upstream group {@code Food & Drink}: food, drink, and dishware. */
  FOOD_AND_DRINK,

  /** Upstream group {@code Travel & Places}: places, buildings, transport, sky and weather. */
  TRAVEL_AND_PLACES,

  /** Upstream group {@code Activities}: events, sports, games, and arts. */
  ACTIVITIES,

  /** Upstream group {@code Objects}: clothing, tools, instruments, and other objects. */
  OBJECTS,

  /** Upstream group {@code Symbols}: signs, arrows, punctuation-like and abstract symbols. */
  SYMBOLS,

  /** Upstream group {@code Flags}: flag emoji; assigned by the derived layer, not by bundled rows. */
  FLAGS
}
