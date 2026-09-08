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
 * The typed relations between {@link Synset synsets} in a WordNet-style lexicon. Readers map
 * their source format's relation names onto these values.
 *
 * <p>Relations that a source format draws between individual word senses (antonymy and
 * derivation, for example) are represented at the synset level: the synset containing the source
 * sense has the relation to the synset containing the target sense.</p>
 *
 * <p>The vocabulary includes every typed synset and sense relation in the
 * <a href="https://globalwordnet.github.io/schemas/WN-LMF-1.4.dtd">Global WordNet Association
 * WN-LMF 1.4 format</a>. Format-specific readers document any contextual mapping.</p>
 *
 * @since 3.0.0
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

  /** A related synset referenced by a see-also relation. */
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
  MEMBER_OF_DOMAIN_USAGE,

  /** An entity that intentionally performs or initiates an event. */
  AGENT,

  /** A state that this concept enters or occupies. */
  BE_IN_STATE,

  /** A semantic class that classifies this concept. */
  CLASSIFIED_BY,

  /** A concept classified by this semantic class. */
  CLASSIFIES,

  /** An instrument that participates with this agent. */
  CO_AGENT_INSTRUMENT,

  /** A patient that participates with this agent. */
  CO_AGENT_PATIENT,

  /** A result that participates with this agent. */
  CO_AGENT_RESULT,

  /** An agent that participates with this instrument. */
  CO_INSTRUMENT_AGENT,

  /** A patient that participates with this instrument. */
  CO_INSTRUMENT_PATIENT,

  /** A result that participates with this instrument. */
  CO_INSTRUMENT_RESULT,

  /** An agent that participates with this patient. */
  CO_PATIENT_AGENT,

  /** An instrument that participates with this patient. */
  CO_PATIENT_INSTRUMENT,

  /** An agent that participates with this result. */
  CO_RESULT_AGENT,

  /** An instrument that participates with this result. */
  CO_RESULT_INSTRUMENT,

  /** A semantic role that participates with this role. */
  CO_ROLE,

  /** A direction associated with an event or entity. */
  DIRECTION,

  /** An equivalent synonym relation from WN-LMF. */
  EQ_SYNONYM,

  /** The location whole that contains this concept. */
  LOCATION_HOLONYM,

  /** The portion whole that contains this concept. */
  PORTION_HOLONYM,

  /** A whole related by an unspecified holonym relation. */
  HOLONYM,

  /** The manner in which this concept occurs. */
  IN_MANNER,

  /** An instrument used in an event. */
  INSTRUMENT,

  /** A location associated with an entity or event. */
  LOCATION,

  /** An event or situation in which this concept is involved. */
  INVOLVED,

  /** The agent involved in an event. */
  INVOLVED_AGENT,

  /** The direction involved in an event. */
  INVOLVED_DIRECTION,

  /** The instrument involved in an event. */
  INVOLVED_INSTRUMENT,

  /** The location involved in an event. */
  INVOLVED_LOCATION,

  /** The patient involved in an event. */
  INVOLVED_PATIENT,

  /** The result involved in an event. */
  INVOLVED_RESULT,

  /** The source direction involved in an event. */
  INVOLVED_SOURCE_DIRECTION,

  /** The target direction involved in an event. */
  INVOLVED_TARGET_DIRECTION,

  /** A synonym relation that is intentionally irregular or non-equivalent. */
  IR_SYNONYM,

  /** An event of which this concept is a subevent. */
  IS_SUBEVENT_OF,

  /** An event for which this concept specifies the manner. */
  MANNER_OF,

  /** A location part contained by this concept. */
  LOCATION_MERONYM,

  /** A portion contained by this concept. */
  PORTION_MERONYM,

  /** A part related by an unspecified meronym relation. */
  MERONYM,

  /** An entity affected by an event. */
  PATIENT,

  /** A concept that restricts this concept. */
  RESTRICTED_BY,

  /** A concept restricted by this concept. */
  RESTRICTS,

  /** A result produced by an event. */
  RESULT,

  /** A semantic role associated with an event. */
  ROLE,

  /** The source direction of an event. */
  SOURCE_DIRECTION,

  /** An event or entity for which this concept is a state. */
  STATE_OF,

  /** An event contained within this event. */
  SUBEVENT,

  /** The target direction of an event. */
  TARGET_DIRECTION,

  /** A feminine form related to this concept. */
  FEMININE,

  /** A concept whose feminine form is this concept. */
  HAS_FEMININE,

  /** A masculine form related to this concept. */
  MASCULINE,

  /** A concept whose masculine form is this concept. */
  HAS_MASCULINE,

  /** A young form related to this concept. */
  YOUNG,

  /** A concept whose young form is this concept. */
  HAS_YOUNG,

  /** A diminutive form related to this concept. */
  DIMINUTIVE,

  /** A concept whose diminutive form is this concept. */
  HAS_DIMINUTIVE,

  /** An augmentative form related to this concept. */
  AUGMENTATIVE,

  /** A concept whose augmentative form is this concept. */
  HAS_AUGMENTATIVE,

  /** A gradable antonym relation. */
  ANTO_GRADABLE,

  /** A simple antonym relation. */
  ANTO_SIMPLE,

  /** A converse antonym relation. */
  ANTO_CONVERSE,

  /** A simple imperfective-to-perfective aspect relation. */
  SIMPLE_ASPECT_IP,

  /** A secondary imperfective-to-perfective aspect relation. */
  SECONDARY_ASPECT_IP,

  /** A simple perfective-to-imperfective aspect relation. */
  SIMPLE_ASPECT_PI,

  /** A secondary perfective-to-imperfective aspect relation. */
  SECONDARY_ASPECT_PI,

  /** A metaphorically related sense. */
  METAPHOR,

  /** A sense for which this sense is a metaphor. */
  HAS_METAPHOR,

  /** A metonymically related sense. */
  METONYM,

  /** A sense for which this sense is a metonym. */
  HAS_METONYM,

  /** The material associated with an entity or event. */
  MATERIAL,

  /** An event associated with a sense. */
  EVENT,

  /** The means by which an event occurs. */
  BY_MEANS_OF,

  /** An entity that undergoes an event. */
  UNDERGOER,

  /** A property expressed by a sense. */
  PROPERTY,

  /** A state expressed by a sense. */
  STATE,

  /** A resource or entity used by an event. */
  USES,

  /** The destination of an event. */
  DESTINATION,

  /** A body part associated with a sense. */
  BODY_PART,

  /** A vehicle associated with a sense. */
  VEHICLE
}
