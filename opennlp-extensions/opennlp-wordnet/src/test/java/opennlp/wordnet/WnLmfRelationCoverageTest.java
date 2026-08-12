/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.wordnet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.WordNetRelation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Conformance pins for every relation type in the GWA WN-LMF 1.4 DTD. */
class WnLmfRelationCoverageTest {

  private static final Map<String, WordNetRelation> SYNSET_RELATIONS = synsetRelations();
  private static final Map<String, WordNetRelation> SENSE_RELATIONS = senseRelations();

  @ParameterizedTest(name = "SynsetRelation {0} maps to {1}")
  @MethodSource("legalSynsetRelations")
  void testEveryLegalSynsetRelation(String relType, WordNetRelation expected) throws IOException {
    final LexicalKnowledgeBase lexicon = parse(synsetDocument(relType, "n"));
    assertEquals(List.of("t-target-n"), lexicon.related("t-source-n", expected));
  }

  @ParameterizedTest(name = "SenseRelation {0} maps to {1}")
  @MethodSource("legalSenseRelations")
  void testEveryLegalSenseRelation(String relType, WordNetRelation expected) throws IOException {
    final LexicalKnowledgeBase lexicon = parse(senseDocument(relType, "v"));
    assertEquals(List.of("t-target-v"), lexicon.related("t-source-v", expected));
  }

  @Test
  void testCoverageTablesMatchWnLmf14Cardinality() {
    // WN-LMF 1.4 declares 85 SynsetRelation values and 48 SenseRelation values.
    // The untyped escape hatch "other" is intentionally skipped, leaving these counts.
    assertEquals(84, SYNSET_RELATIONS.size());
    assertEquals(47, SENSE_RELATIONS.size());
  }

  @Test
  void testSimilarKeepsSynsetAndSenseSemanticsSeparate() throws IOException {
    final LexicalKnowledgeBase synsetVerb = parse(synsetDocument("similar", "v"));
    assertEquals(List.of("t-target-v"),
        synsetVerb.related("t-source-v", WordNetRelation.VERB_GROUP));

    final LexicalKnowledgeBase senseVerb = parse(senseDocument("similar", "v"));
    assertEquals(List.of("t-target-v"),
        senseVerb.related("t-source-v", WordNetRelation.SIMILAR_TO));
    assertTrue(senseVerb.related("t-source-v", WordNetRelation.VERB_GROUP).isEmpty());
  }

  @Test
  void testOtherRemainsAnExplicitlyUntypedEscapeHatch() throws IOException {
    assertTrue(parse(synsetDocument("other", "n")).synset("t-source-n")
        .orElseThrow().relations().isEmpty());
    assertTrue(parse(senseDocument("other", "n")).synset("t-source-n")
        .orElseThrow().relations().isEmpty());
  }

  @Test
  void testRelationNamesRemainCaseSensitive() {
    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> parse(synsetDocument("Hypernym", "n")));
    assertTrue(error.getMessage().contains("Unknown relation type Hypernym"));
  }

  @Test
  void testRejectsSenseOnlyRelationOnSynset() {
    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> parse(synsetDocument("body_part", "n")));
    assertTrue(error.getMessage().contains(
        "Relation type body_part is not legal on SynsetRelation"));
  }

  @Test
  void testRejectsSynsetOnlyRelationOnSense() {
    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> parse(senseDocument("hypernym", "n")));
    assertTrue(error.getMessage().contains(
        "Relation type hypernym is not legal on SenseRelation"));
  }

  @Test
  void testDuplicateRelationTargetsAreDeduplicatedInSourceOrder() throws IOException {
    final String body = entries("n")
        + "<Synset id=\"t-source-n\" partOfSpeech=\"n\">"
        + "<SynsetRelation relType=\"agent\" target=\"t-target-n\"/>"
        + "<SynsetRelation relType=\"agent\" target=\"t-target2-n\"/>"
        + "<SynsetRelation relType=\"agent\" target=\"t-target-n\"/>"
        + "</Synset>"
        + "<LexicalEntry id=\"t-target2-entry-n\"><Lemma writtenForm=\"third\" "
        + "partOfSpeech=\"n\"/><Sense id=\"t-target2-sense-n\" "
        + "synset=\"t-target2-n\"/></LexicalEntry>"
        + "<Synset id=\"t-target-n\" partOfSpeech=\"n\"/>"
        + "<Synset id=\"t-target2-n\" partOfSpeech=\"n\"/>";
    final LexicalKnowledgeBase lexicon = parse(wrap(body));
    assertEquals(List.of("t-target-n", "t-target2-n"),
        lexicon.related("t-source-n", WordNetRelation.AGENT));
  }

  static Stream<Arguments> legalSynsetRelations() {
    return SYNSET_RELATIONS.entrySet().stream()
        .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
  }

  static Stream<Arguments> legalSenseRelations() {
    return SENSE_RELATIONS.entrySet().stream()
        .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
  }

  private static LexicalKnowledgeBase parse(String document) throws IOException {
    return WnLmfReader.read(
        new ByteArrayInputStream(document.getBytes(StandardCharsets.UTF_8)), "relations.xml");
  }

  private static String synsetDocument(String relType, String pos) {
    return wrap(entries(pos)
        + "<Synset id=\"t-source-" + pos + "\" partOfSpeech=\"" + pos + "\">"
        + "<SynsetRelation relType=\"" + relType + "\" target=\"t-target-" + pos
        + "\"/></Synset>"
        + "<Synset id=\"t-target-" + pos + "\" partOfSpeech=\"" + pos + "\"/>");
  }

  private static String senseDocument(String relType, String pos) {
    return wrap("<LexicalEntry id=\"t-source-entry-" + pos + "\">"
        + "<Lemma writtenForm=\"source\" partOfSpeech=\"" + pos + "\"/>"
        + "<Sense id=\"t-source-sense-" + pos + "\" synset=\"t-source-" + pos + "\">"
        + "<SenseRelation relType=\"" + relType + "\" target=\"t-target-sense-" + pos
        + "\"/></Sense></LexicalEntry>"
        + "<LexicalEntry id=\"t-target-entry-" + pos + "\">"
        + "<Lemma writtenForm=\"target\" partOfSpeech=\"" + pos + "\"/>"
        + "<Sense id=\"t-target-sense-" + pos + "\" synset=\"t-target-" + pos
        + "\"/></LexicalEntry>"
        + "<Synset id=\"t-source-" + pos + "\" partOfSpeech=\"" + pos + "\"/>"
        + "<Synset id=\"t-target-" + pos + "\" partOfSpeech=\"" + pos + "\"/>");
  }

  private static String entries(String pos) {
    return "<LexicalEntry id=\"t-source-entry-" + pos + "\">"
        + "<Lemma writtenForm=\"source\" partOfSpeech=\"" + pos + "\"/>"
        + "<Sense id=\"t-source-sense-" + pos + "\" synset=\"t-source-" + pos
        + "\"/></LexicalEntry>"
        + "<LexicalEntry id=\"t-target-entry-" + pos + "\">"
        + "<Lemma writtenForm=\"target\" partOfSpeech=\"" + pos + "\"/>"
        + "<Sense id=\"t-target-sense-" + pos + "\" synset=\"t-target-" + pos
        + "\"/></LexicalEntry>";
  }

  private static String wrap(String body) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<LexicalResource><Lexicon id=\"t\" label=\"test\" language=\"en\" version=\"1\">"
        + body + "</Lexicon></LexicalResource>";
  }

  private static Map<String, WordNetRelation> synsetRelations() {
    final Map<String, WordNetRelation> relations = commonRelations();
    relations.put("attribute", WordNetRelation.ATTRIBUTE);
    relations.put("be_in_state", WordNetRelation.BE_IN_STATE);
    relations.put("causes", WordNetRelation.CAUSE);
    relations.put("classified_by", WordNetRelation.CLASSIFIED_BY);
    relations.put("classifies", WordNetRelation.CLASSIFIES);
    relations.put("co_agent_instrument", WordNetRelation.CO_AGENT_INSTRUMENT);
    relations.put("co_agent_patient", WordNetRelation.CO_AGENT_PATIENT);
    relations.put("co_agent_result", WordNetRelation.CO_AGENT_RESULT);
    relations.put("co_instrument_agent", WordNetRelation.CO_INSTRUMENT_AGENT);
    relations.put("co_instrument_patient", WordNetRelation.CO_INSTRUMENT_PATIENT);
    relations.put("co_instrument_result", WordNetRelation.CO_INSTRUMENT_RESULT);
    relations.put("co_patient_agent", WordNetRelation.CO_PATIENT_AGENT);
    relations.put("co_patient_instrument", WordNetRelation.CO_PATIENT_INSTRUMENT);
    relations.put("co_result_agent", WordNetRelation.CO_RESULT_AGENT);
    relations.put("co_result_instrument", WordNetRelation.CO_RESULT_INSTRUMENT);
    relations.put("co_role", WordNetRelation.CO_ROLE);
    relations.put("direction", WordNetRelation.DIRECTION);
    relations.put("entails", WordNetRelation.ENTAILMENT);
    relations.put("eq_synonym", WordNetRelation.EQ_SYNONYM);
    relations.put("holo_location", WordNetRelation.LOCATION_HOLONYM);
    relations.put("holo_member", WordNetRelation.MEMBER_HOLONYM);
    relations.put("holo_part", WordNetRelation.PART_HOLONYM);
    relations.put("holo_portion", WordNetRelation.PORTION_HOLONYM);
    relations.put("holo_substance", WordNetRelation.SUBSTANCE_HOLONYM);
    relations.put("holonym", WordNetRelation.HOLONYM);
    relations.put("hypernym", WordNetRelation.HYPERNYM);
    relations.put("hyponym", WordNetRelation.HYPONYM);
    relations.put("in_manner", WordNetRelation.IN_MANNER);
    relations.put("instance_hypernym", WordNetRelation.INSTANCE_HYPERNYM);
    relations.put("instance_hyponym", WordNetRelation.INSTANCE_HYPONYM);
    relations.put("involved", WordNetRelation.INVOLVED);
    relations.put("involved_agent", WordNetRelation.INVOLVED_AGENT);
    relations.put("involved_direction", WordNetRelation.INVOLVED_DIRECTION);
    relations.put("involved_instrument", WordNetRelation.INVOLVED_INSTRUMENT);
    relations.put("involved_location", WordNetRelation.INVOLVED_LOCATION);
    relations.put("involved_patient", WordNetRelation.INVOLVED_PATIENT);
    relations.put("involved_result", WordNetRelation.INVOLVED_RESULT);
    relations.put("involved_source_direction", WordNetRelation.INVOLVED_SOURCE_DIRECTION);
    relations.put("involved_target_direction", WordNetRelation.INVOLVED_TARGET_DIRECTION);
    relations.put("ir_synonym", WordNetRelation.IR_SYNONYM);
    relations.put("is_caused_by", WordNetRelation.CAUSED_BY);
    relations.put("is_entailed_by", WordNetRelation.ENTAILED_BY);
    relations.put("is_subevent_of", WordNetRelation.IS_SUBEVENT_OF);
    relations.put("manner_of", WordNetRelation.MANNER_OF);
    relations.put("mero_location", WordNetRelation.LOCATION_MERONYM);
    relations.put("mero_member", WordNetRelation.MEMBER_MERONYM);
    relations.put("mero_part", WordNetRelation.PART_MERONYM);
    relations.put("mero_portion", WordNetRelation.PORTION_MERONYM);
    relations.put("mero_substance", WordNetRelation.SUBSTANCE_MERONYM);
    relations.put("meronym", WordNetRelation.MERONYM);
    relations.put("patient", WordNetRelation.PATIENT);
    relations.put("restricted_by", WordNetRelation.RESTRICTED_BY);
    relations.put("restricts", WordNetRelation.RESTRICTS);
    relations.put("role", WordNetRelation.ROLE);
    relations.put("similar", WordNetRelation.SIMILAR_TO);
    relations.put("source_direction", WordNetRelation.SOURCE_DIRECTION);
    relations.put("state_of", WordNetRelation.STATE_OF);
    relations.put("subevent", WordNetRelation.SUBEVENT);
    relations.put("target_direction", WordNetRelation.TARGET_DIRECTION);
    return Map.copyOf(relations);
  }

  private static Map<String, WordNetRelation> senseRelations() {
    final Map<String, WordNetRelation> relations = commonRelations();
    relations.put("body_part", WordNetRelation.BODY_PART);
    relations.put("by_means_of", WordNetRelation.BY_MEANS_OF);
    relations.put("derivation", WordNetRelation.DERIVATIONALLY_RELATED);
    relations.put("destination", WordNetRelation.DESTINATION);
    relations.put("event", WordNetRelation.EVENT);
    relations.put("has_metaphor", WordNetRelation.HAS_METAPHOR);
    relations.put("has_metonym", WordNetRelation.HAS_METONYM);
    relations.put("material", WordNetRelation.MATERIAL);
    relations.put("metaphor", WordNetRelation.METAPHOR);
    relations.put("metonym", WordNetRelation.METONYM);
    relations.put("participle", WordNetRelation.PARTICIPLE);
    relations.put("pertainym", WordNetRelation.PERTAINYM);
    relations.put("property", WordNetRelation.PROPERTY);
    relations.put("secondary_aspect_ip", WordNetRelation.SECONDARY_ASPECT_IP);
    relations.put("secondary_aspect_pi", WordNetRelation.SECONDARY_ASPECT_PI);
    relations.put("similar", WordNetRelation.SIMILAR_TO);
    relations.put("simple_aspect_ip", WordNetRelation.SIMPLE_ASPECT_IP);
    relations.put("simple_aspect_pi", WordNetRelation.SIMPLE_ASPECT_PI);
    relations.put("state", WordNetRelation.STATE);
    relations.put("undergoer", WordNetRelation.UNDERGOER);
    relations.put("uses", WordNetRelation.USES);
    relations.put("vehicle", WordNetRelation.VEHICLE);
    return Map.copyOf(relations);
  }

  private static Map<String, WordNetRelation> commonRelations() {
    final Map<String, WordNetRelation> relations = new LinkedHashMap<>();
    relations.put("agent", WordNetRelation.AGENT);
    relations.put("also", WordNetRelation.ALSO_SEE);
    relations.put("anto_converse", WordNetRelation.ANTO_CONVERSE);
    relations.put("anto_gradable", WordNetRelation.ANTO_GRADABLE);
    relations.put("anto_simple", WordNetRelation.ANTO_SIMPLE);
    relations.put("antonym", WordNetRelation.ANTONYM);
    relations.put("augmentative", WordNetRelation.AUGMENTATIVE);
    relations.put("diminutive", WordNetRelation.DIMINUTIVE);
    relations.put("domain_region", WordNetRelation.DOMAIN_REGION);
    relations.put("domain_topic", WordNetRelation.DOMAIN_TOPIC);
    relations.put("exemplifies", WordNetRelation.DOMAIN_USAGE);
    relations.put("feminine", WordNetRelation.FEMININE);
    relations.put("has_augmentative", WordNetRelation.HAS_AUGMENTATIVE);
    relations.put("has_diminutive", WordNetRelation.HAS_DIMINUTIVE);
    relations.put("has_domain_region", WordNetRelation.MEMBER_OF_DOMAIN_REGION);
    relations.put("has_domain_topic", WordNetRelation.MEMBER_OF_DOMAIN_TOPIC);
    relations.put("has_feminine", WordNetRelation.HAS_FEMININE);
    relations.put("has_masculine", WordNetRelation.HAS_MASCULINE);
    relations.put("has_young", WordNetRelation.HAS_YOUNG);
    relations.put("instrument", WordNetRelation.INSTRUMENT);
    relations.put("is_exemplified_by", WordNetRelation.MEMBER_OF_DOMAIN_USAGE);
    relations.put("location", WordNetRelation.LOCATION);
    relations.put("masculine", WordNetRelation.MASCULINE);
    relations.put("result", WordNetRelation.RESULT);
    relations.put("young", WordNetRelation.YOUNG);
    return relations;
  }
}
