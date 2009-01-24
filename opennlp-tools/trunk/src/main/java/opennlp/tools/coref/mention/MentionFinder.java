/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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


package opennlp.tools.coref.mention;

/**
 * Specifies the interface that Objects which determine the space of
 * mentions for coreference should implement.
 */
public interface MentionFinder {

  /**
   * Specifies whether pre-nominal named-entities should be collected as mentions.
   *
   * @param collectPrenominalNamedEntities true if pre-nominal named-entities should be collected; false otherwise.
   */
  public void setPrenominalNamedEntityCollection(boolean collectPrenominalNamedEntities);

  /**
   * Returns whether this mention finder collects pre-nominal named-entities as mentions.
   *
   * @return true if this mention finder collects pre-nominal named-entities as mentions
   */
  public boolean isPrenominalNamedEntityCollection();

  /**
   * Returns whether this mention finder collects coordinated noun phrases as mentions.
   *
   * @return true if this mention finder collects coordinated noun phrases as mentions; false otherwise.
   */
  public boolean isCoordinatedNounPhraseCollection();

  /**
   * Specifies whether coordinated noun phrases should be collected as mentions.
   *
   * @param collectCoordinatedNounPhrases true if coordinated noun phrases should be collected; false otherwise.
   */
  public void setCoordinatedNounPhraseCollection(boolean collectCoordinatedNounPhrases);

  /**
   * Returns an array of mentions.
   *
   * @param parse A top level parse from which mentions are gathered.
   *
   * @return an array of mentions which implement the <code>Extent</code> interface.
   */
  public Mention[] getMentions(Parse parse);
}
