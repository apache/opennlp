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
package opennlp.tools.wordnet;

/**
 * The typed relations a wordnet-style lexicon draws between {@link Synset synsets}. Readers map
 * their source format's relation names onto these values.
 *
 * <p>Relations that a source format draws between individual word senses (antonymy and
 * derivation, for example) surface here at the synset level: the synset containing the source
 * sense carries the relation to the synset containing the target sense.</p>
 */
public enum WordNetRelation {

  /** Opposition in meaning, for example between the adjectives for tall and short. */
  ANTONYM,

  /** The more general concept: a dog is a kind of canid. */
  HYPERNYM,

  /** The class a named instance belongs to: a specific river is an instance of river. */
  INSTANCE_HYPERNYM,

  /** The more specific concept: canid has the hyponym dog. */
  HYPONYM,

  /** A named instance of this class. */
  INSTANCE_HYPONYM,

  /** The group this synset is a member of. */
  MEMBER_HOLONYM,

  /** The whole this synset is a substance of. */
  SUBSTANCE_HOLONYM,

  /** The whole this synset is a part of. */
  PART_HOLONYM,

  /** A member of this group. */
  MEMBER_MERONYM,

  /** A substance this synset is made of. */
  SUBSTANCE_MERONYM,

  /** A part of this synset. */
  PART_MERONYM,

  /** The attribute a value expresses, or a value of this attribute. */
  ATTRIBUTE,

  /** A derivationally related form, typically across parts of speech. */
  DERIVATIONALLY_RELATED,

  /** An action entailed by this verb: snoring entails sleeping. */
  ENTAILMENT,

  /** The verb that entails this one; the inverse of {@link #ENTAILMENT}. */
  ENTAILED_BY,

  /** An effect this verb causes. */
  CAUSE,

  /** The cause of this verb; the inverse of {@link #CAUSE}. */
  CAUSED_BY,

  /** A related synset worth consulting. */
  ALSO_SEE,

  /** A verb sense grouped with this one. */
  VERB_GROUP,

  /** A satellite or head adjective in the same similarity cluster. */
  SIMILAR_TO,

  /** The verb an adjective is the participle of. */
  PARTICIPLE,

  /**
   * The noun an adjective pertains to, or the adjective an adverb derives from. The source
   * formats use one pointer for both directions of derivation, so this value does too.
   */
  PERTAINYM,

  /** The topical domain this synset belongs to. */
  DOMAIN_TOPIC,

  /** A synset belonging to this topical domain. */
  MEMBER_OF_DOMAIN_TOPIC,

  /** The regional domain this synset belongs to. */
  DOMAIN_REGION,

  /** A synset belonging to this regional domain. */
  MEMBER_OF_DOMAIN_REGION,

  /** The usage domain this synset belongs to, for example slang or archaism. */
  DOMAIN_USAGE,

  /** A synset belonging to this usage domain. */
  MEMBER_OF_DOMAIN_USAGE
}
