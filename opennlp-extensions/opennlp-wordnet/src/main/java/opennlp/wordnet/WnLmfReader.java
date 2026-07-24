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
package opennlp.wordnet;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.Synset;
import opennlp.tools.wordnet.WordNetPOS;
import opennlp.tools.wordnet.WordNetRelation;

/**
 * Reads a WN-LMF XML document (the Global WordNet Association
 * <a href="https://globalwordnet.github.io/schemas/">interchange format</a>, used by
 * <a href="https://github.com/globalwordnet/english-wordnet">Open English WordNet</a> and many
 * other language wordnets) into a {@link LexicalKnowledgeBase} using the JDK StAX parser.
 *
 * <p>It reads the subset of the format the contract serves: lexical entries, synsets with their
 * definitions and typed relations, and sense relations, which are lifted to the synset level as
 * documented on {@link WordNetRelation}. Elements outside that subset are skipped, as are
 * relations of type {@code other} (the format's untyped escape hatch); any other unknown
 * relation type fails loud.</p>
 *
 * <p>The parser is hardened against XXE: DTD processing and external entities are disabled, so a
 * DOCTYPE is skipped but nothing it names is fetched or resolved.</p>
 *
 * <p>Malformed structure fails loud with an {@link InvalidFormatException} naming the resource
 * and, where the parser provides one, the line; I/O failures propagate as {@link IOException}.
 * Part-of-speech code {@code s} normalizes to {@link WordNetPOS#ADJECTIVE}, and a {@code similar}
 * relation on a verb synset maps to {@link WordNetRelation#VERB_GROUP} rather than
 * {@link WordNetRelation#SIMILAR_TO}. The returned lexicon is immutable and safe for concurrent
 * lookups.</p>
 */
public final class WnLmfReader {

  private static final Map<String, WordNetRelation> RELATION_NAMES = relationNames();

  /** The format's escape-hatch relation type; carries no type the contract can express. */
  private static final String OTHER_RELATION = "other";

  /** The element declaring a lexical entry; opened and closed by the same handlers. */
  private static final String LEXICAL_ENTRY_ELEMENT = "LexicalEntry";

  /** The element declaring a sense; opened and closed by the same handlers. */
  private static final String SENSE_ELEMENT = "Sense";

  /** The element declaring a synset; opened and closed by the same handlers. */
  private static final String SYNSET_ELEMENT = "Synset";

  /** Not instantiable. */
  private WnLmfReader() {
  }

  /**
   * Reads a WN-LMF XML file.
   *
   * @param file The XML file. Must not be {@code null} and must exist.
   * @return The loaded lexicon.
   * @throws IllegalArgumentException Thrown if {@code file} is {@code null} or missing.
   * @throws InvalidFormatException Thrown if the document is malformed; the message names the
   *     file and, where available, the line.
   * @throws IOException Thrown if reading the file fails.
   */
  public static LexicalKnowledgeBase read(Path file) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("File must not be null");
    }
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("File does not exist or is not a regular file: " + file);
    }
    try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
      return read(in, file.toString());
    }
  }

  /**
   * Reads a WN-LMF XML document from a stream. The stream is not closed.
   *
   * @param in           The document stream. Must not be {@code null}.
   * @param resourceName The name used in error messages. Must not be {@code null}.
   * @return The loaded lexicon.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}.
   * @throws InvalidFormatException Thrown if the document is malformed; the message names the
   *     resource and, where available, the line.
   * @throws IOException Thrown if reading the stream fails.
   */
  public static LexicalKnowledgeBase read(InputStream in, String resourceName) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("In must not be null");
    }
    if (resourceName == null) {
      throw new IllegalArgumentException("ResourceName must not be null");
    }
    final Parser parser = new Parser(resourceName);
    try {
      final XMLStreamReader reader = hardenedFactory().createXMLStreamReader(in);
      try {
        parser.parse(reader);
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      // StAX wraps a failing stream read in an XMLStreamException; surface it as the I/O failure.
      final Throwable nested = e.getNestedException() == null ? e.getCause()
          : e.getNestedException();
      if (nested instanceof IOException io) {
        throw io;
      }
      throw parser.malformed(e.getLocation(), "XML error: " + e.getMessage(), e);
    }
    return parser.build();
  }

  /**
   * Builds an XXE-hardened StAX factory: the DTD internal subset is not processed and external
   * entities and the external DTD subset are denied, so a DOCTYPE is skipped but never resolved.
   *
   * @return The hardened factory.
   */
  private static XMLInputFactory hardenedFactory() {
    final XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
    factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
      throw new XMLStreamException("External entity resolution is disabled, refusing " + systemId);
    });
    return factory;
  }

  /** Holds the streaming parse state and performs post-parse resolution. */
  private static final class Parser {

    private final String resourceName;

    // Entry state.
    private final Set<String> entryIds = new HashSet<>();
    private final Map<String, String> lemmaByEntryId = new HashMap<>();
    private final Map<String, WordNetPOS> posByEntryId = new HashMap<>();
    private final Map<String, String> synsetBySenseId = new HashMap<>();
    private final Map<InMemoryWordNetLexicon.LemmaKey, List<String>> senseOrder =
        new LinkedHashMap<>();
    private final List<RawSenseRelation> senseRelations = new ArrayList<>();
    private final Map<String, RawSynset> rawSynsets = new LinkedHashMap<>();
    // Fallback membership (entry ids per synset in document order) when members is absent.
    private final Map<String, List<String>> entryIdsBySynset = new HashMap<>();

    // Cursor state.
    private String currentEntryId;
    private String currentEntryLemma;
    private WordNetPOS currentEntryPos;
    private String currentSenseId;
    private RawSynset currentSynset;

    /**
     * Creates a parser.
     *
     * @param resourceName The name used in error messages.
     */
    Parser(String resourceName) {
      this.resourceName = resourceName;
    }

    /**
     * Streams the document, dispatching start and end elements.
     *
     * @param reader The StAX reader.
     * @throws XMLStreamException Thrown if the stream read fails.
     * @throws InvalidFormatException Thrown if the document is malformed.
     */
    void parse(XMLStreamReader reader) throws XMLStreamException, InvalidFormatException {
      while (reader.hasNext()) {
        final int event = reader.next();
        // A DTD event carries nothing that can affect parsing once the factory is hardened.
        if (event == XMLStreamConstants.START_ELEMENT) {
          startElement(reader);
        } else if (event == XMLStreamConstants.END_ELEMENT) {
          endElement(reader.getLocalName());
        }
      }
    }

    /**
     * Handles one start element, updating cursor state and collecting raw entries, senses, and
     * synsets.
     *
     * @param reader The StAX reader positioned on the start element.
     * @throws XMLStreamException Thrown if reading element text fails.
     * @throws InvalidFormatException Thrown if the element violates the format.
     */
    private void startElement(XMLStreamReader reader)
        throws XMLStreamException, InvalidFormatException {
      final String name = reader.getLocalName();
      switch (name) {
        case LEXICAL_ENTRY_ELEMENT -> {
          currentEntryId = requireAttribute(reader, "id");
          if (!entryIds.add(currentEntryId)) {
            throw malformed(reader.getLocation(),
                "Duplicate lexical entry id " + currentEntryId, null);
          }
          currentEntryLemma = null;
          currentEntryPos = null;
        }
        case "Lemma" -> {
          if (currentEntryId == null) {
            throw malformed(reader.getLocation(), "Lemma outside a LexicalEntry", null);
          }
          currentEntryLemma = requireAttribute(reader, "writtenForm");
          currentEntryPos = parsePos(requireAttribute(reader, "partOfSpeech"),
              reader.getLocation());
          lemmaByEntryId.put(currentEntryId, currentEntryLemma);
          posByEntryId.put(currentEntryId, currentEntryPos);
        }
        case SENSE_ELEMENT -> {
          if (currentEntryLemma == null) {
            throw malformed(reader.getLocation(),
                "Sense before its entry's Lemma in LexicalEntry " + currentEntryId, null);
          }
          currentSenseId = requireAttribute(reader, "id");
          final String synsetId = requireAttribute(reader, "synset");
          if (synsetBySenseId.putIfAbsent(currentSenseId, synsetId) != null) {
            throw malformed(reader.getLocation(), "Duplicate sense id " + currentSenseId, null);
          }
          entryIdsBySynset.computeIfAbsent(synsetId, unused -> new ArrayList<>(2))
              .add(currentEntryId);
          final List<String> order = senseOrder.computeIfAbsent(
              InMemoryWordNetLexicon.LemmaKey.of(currentEntryLemma, currentEntryPos),
              unused -> new ArrayList<>(2));
          if (!order.contains(synsetId)) {
            order.add(synsetId);
          }
        }
        case "SenseRelation" -> {
          if (currentSenseId == null) {
            throw malformed(reader.getLocation(), "SenseRelation outside a Sense", null);
          }
          senseRelations.add(new RawSenseRelation(currentSenseId,
              requireAttribute(reader, "relType"), requireAttribute(reader, "target"),
              line(reader.getLocation())));
        }
        case SYNSET_ELEMENT -> {
          final String id = requireAttribute(reader, "id");
          final WordNetPOS pos = parsePos(requireAttribute(reader, "partOfSpeech"),
              reader.getLocation());
          currentSynset = new RawSynset(id, pos, reader.getAttributeValue(null, "members"),
              line(reader.getLocation()));
          if (rawSynsets.putIfAbsent(id, currentSynset) != null) {
            throw malformed(reader.getLocation(), "Duplicate synset id " + id, null);
          }
        }
        case "Definition" -> {
          if (currentSynset != null && currentSynset.gloss == null) {
            currentSynset.gloss = reader.getElementText();
          }
        }
        case "SynsetRelation" -> {
          if (currentSynset == null) {
            throw malformed(reader.getLocation(), "SynsetRelation outside a Synset", null);
          }
          final String relType = requireAttribute(reader, "relType");
          final String target = requireAttribute(reader, "target");
          // The escape-hatch type is a documented skip, not a rejection.
          if (!OTHER_RELATION.equals(relType)) {
            currentSynset.relations.add(
                new RawRelation(relType, target, line(reader.getLocation())));
          }
        }
        default -> {
          // Pronunciation, Form, Example, SyntacticBehaviour, ILIDefinition, and other
          // elements outside the contract subset are skipped.
        }
      }
    }

    /**
     * Clears cursor state when a tracked element closes.
     *
     * @param name The local name of the closing element.
     */
    private void endElement(String name) {
      switch (name) {
        case LEXICAL_ENTRY_ELEMENT -> {
          currentEntryId = null;
          currentEntryLemma = null;
          currentEntryPos = null;
        }
        case SENSE_ELEMENT -> currentSenseId = null;
        case SYNSET_ELEMENT -> currentSynset = null;
        default -> {
          // Nothing to close for skipped elements.
        }
      }
    }

    /**
     * Resolves the collected raw state into an immutable lexicon: validates sense targets, lifts
     * sense relations to the synset level, and materializes the contract synsets.
     *
     * @return The loaded lexicon.
     * @throws InvalidFormatException Thrown if a sense or relation references an undeclared
     *     target, or a synset has no members.
     */
    LexicalKnowledgeBase build() throws InvalidFormatException {
      // Every sense must point to a declared synset, with a consistent part of speech.
      for (final Map.Entry<String, String> sense : synsetBySenseId.entrySet()) {
        final RawSynset target = rawSynsets.get(sense.getValue());
        if (target == null) {
          throw malformed(null,
              "Sense " + sense.getKey() + " references undeclared synset " + sense.getValue(),
              null);
        }
      }
      // Lift sense relations to the synset level.
      for (final RawSenseRelation relation : senseRelations) {
        if (OTHER_RELATION.equals(relation.relType)) {
          continue;
        }
        final String sourceSynsetId = synsetBySenseId.get(relation.sourceSenseId);
        final String targetSynsetId = synsetBySenseId.get(relation.targetSenseId);
        if (targetSynsetId == null) {
          throw malformed(null, "SenseRelation at line " + relation.line + " from sense "
              + relation.sourceSenseId + " references undeclared sense " + relation.targetSenseId,
              null);
        }
        final RawSynset source = rawSynsets.get(sourceSynsetId);
        source.relations.add(new RawRelation(relation.relType, targetSynsetId, relation.line));
      }
      // Resolve raw synsets into contract synsets.
      final Map<String, Synset> synsetsById = new LinkedHashMap<>(rawSynsets.size() * 2);
      for (final RawSynset raw : rawSynsets.values()) {
        final Map<WordNetRelation, List<String>> relations = resolveRelations(raw);
        synsetsById.put(raw.id,
            new Synset(raw.id, raw.pos, memberLemmas(raw), raw.gloss == null ? "" : raw.gloss,
                relations));
      }
      return new InMemoryWordNetLexicon(synsetsById, senseOrder);
    }

    /**
     * Resolves a raw synset's relations into typed target-id lists, deduplicated in source order.
     *
     * @param raw The raw synset.
     * @return The typed relations for the contract synset.
     * @throws InvalidFormatException Thrown if a relation type is unknown or its target is
     *     undeclared.
     */
    private Map<WordNetRelation, List<String>> resolveRelations(RawSynset raw)
        throws InvalidFormatException {
      final Map<WordNetRelation, LinkedHashSet<String>> typed = new LinkedHashMap<>();
      for (final RawRelation relation : raw.relations) {
        final WordNetRelation type = parseRelation(relation.relType, raw.pos, relation.line);
        final RawSynset target = rawSynsets.get(relation.target);
        if (target == null) {
          throw malformed(null, "Relation " + relation.relType + " at line " + relation.line
              + " on synset " + raw.id + " references undeclared synset " + relation.target, null);
        }
        // Share the synset table's id instance so only one copy of each id is retained.
        typed.computeIfAbsent(type, unused -> new LinkedHashSet<>()).add(target.id);
      }
      final Map<WordNetRelation, List<String>> relations = new LinkedHashMap<>(typed.size() * 2);
      for (final Map.Entry<WordNetRelation, LinkedHashSet<String>> entry : typed.entrySet()) {
        relations.put(entry.getKey(), List.copyOf(entry.getValue()));
      }
      return relations;
    }

    /**
     * Resolves a synset's member entry ids to their lemmas, from the {@code members} attribute
     * when present and otherwise from the senses that pointed at the synset.
     *
     * @param raw The raw synset.
     * @return The member lemmas in source order, deduplicated.
     * @throws InvalidFormatException Thrown if the synset has no members, names an undeclared
     *     entry, or a member's part of speech disagrees with the synset's.
     */
    private List<String> memberLemmas(RawSynset raw) throws InvalidFormatException {
      final List<String> entryIds;
      if (raw.members != null && !raw.members.isEmpty()) {
        entryIds = LemmaFolding.splitOnSpaces(raw.members);
      } else {
        final List<String> fromSenses = entryIdsBySynset.get(raw.id);
        entryIds = fromSenses == null ? List.of() : fromSenses;
      }
      if (entryIds.isEmpty()) {
        throw malformed(null, "Synset " + raw.id + " at line " + raw.line
            + " has no member entries", null);
      }
      final List<String> lemmas = new ArrayList<>(entryIds.size());
      for (final String entryId : entryIds) {
        final String lemma = lemmaByEntryId.get(entryId);
        if (lemma == null) {
          throw malformed(null, "Synset " + raw.id + " at line " + raw.line
              + " lists undeclared member entry " + entryId, null);
        }
        if (raw.pos != posByEntryId.get(entryId)) {
          throw malformed(null, "Synset " + raw.id + " at line " + raw.line
              + " has part of speech " + raw.pos + " but member entry " + entryId
              + " has " + posByEntryId.get(entryId), null);
        }
        if (!lemmas.contains(lemma)) {
          lemmas.add(lemma);
        }
      }
      return lemmas;
    }

    /**
     * Maps a WN-LMF part-of-speech code to a {@link WordNetPOS}; code {@code s} normalizes to
     * {@link WordNetPOS#ADJECTIVE}.
     *
     * @param code     The part-of-speech code.
     * @param location The parser location, for error reporting.
     * @return The part of speech.
     * @throws InvalidFormatException Thrown if the code is unknown.
     */
    private WordNetPOS parsePos(String code, Location location) throws InvalidFormatException {
      return switch (code) {
        case "n" -> WordNetPOS.NOUN;
        case "v" -> WordNetPOS.VERB;
        case "a", "s" -> WordNetPOS.ADJECTIVE;
        case "r" -> WordNetPOS.ADVERB;
        default -> throw malformed(location, "Unknown part-of-speech code: " + code, null);
      };
    }

    /**
     * Maps a WN-LMF relation name to a {@link WordNetRelation}. A {@code similar} relation on a
     * verb synset maps to {@link WordNetRelation#VERB_GROUP}, otherwise to
     * {@link WordNetRelation#SIMILAR_TO}.
     *
     * @param relType   The relation name.
     * @param sourcePos The part of speech of the source synset.
     * @param line      The document line, for error reporting.
     * @return The mapped relation.
     * @throws InvalidFormatException Thrown if the relation name is unknown.
     */
    private WordNetRelation parseRelation(String relType, WordNetPOS sourcePos, int line)
        throws InvalidFormatException {
      if ("similar".equals(relType)) {
        return sourcePos == WordNetPOS.VERB ? WordNetRelation.VERB_GROUP
            : WordNetRelation.SIMILAR_TO;
      }
      final WordNetRelation relation = RELATION_NAMES.get(relType);
      if (relation == null) {
        throw malformed(null, "Unknown relation type " + relType + " at line " + line, null);
      }
      return relation;
    }

    /**
     * Reads a required attribute from the current element.
     *
     * @param reader    The StAX reader.
     * @param attribute The attribute name.
     * @return The non-empty attribute value.
     * @throws InvalidFormatException Thrown if the attribute is absent or empty.
     */
    private String requireAttribute(XMLStreamReader reader, String attribute)
        throws InvalidFormatException {
      final String value = reader.getAttributeValue(null, attribute);
      if (value == null || value.isEmpty()) {
        throw malformed(reader.getLocation(), "Element " + reader.getLocalName()
            + " is missing required attribute " + attribute, null);
      }
      return value;
    }

    /**
     * Builds a malformed-document exception naming the resource and, when known, the line.
     *
     * @param location The parser location, or {@code null} when unavailable.
     * @param message  The failure detail.
     * @param cause    The underlying cause, or {@code null}.
     * @return The exception to throw.
     */
    InvalidFormatException malformed(Location location, String message, Throwable cause) {
      final int line = line(location);
      final String prefix = line < 0 ? "Malformed WN-LMF document " + resourceName + ": "
          : "Malformed WN-LMF document " + resourceName + " at line " + line + ": ";
      return cause == null ? new InvalidFormatException(prefix + message)
          : new InvalidFormatException(prefix + message, cause);
    }

    /**
     * Extracts a line number from a parser location.
     *
     * @param location The location, or {@code null}.
     * @return The line number, or {@code -1} when unknown.
     */
    private static int line(Location location) {
      return location == null ? -1 : location.getLineNumber();
    }
  }

  private static final class RawSynset {
    private final String id;
    private final WordNetPOS pos;
    private final String members;
    private final int line;
    private final List<RawRelation> relations = new ArrayList<>(4);
    private String gloss;

    /**
     * Creates a raw synset gathered during parsing.
     *
     * @param id      The synset id.
     * @param pos     The part of speech.
     * @param members The {@code members} attribute value, or {@code null} when absent.
     * @param line    The document line.
     */
    RawSynset(String id, WordNetPOS pos, String members, int line) {
      this.id = id;
      this.pos = pos;
      this.members = members;
      this.line = line;
    }
  }

  /** A parsed synset relation, kept until the target synset is known. */
  private record RawRelation(String relType, String target, int line) {
  }

  /** A parsed sense relation, kept until both sense ids are known. */
  private record RawSenseRelation(String sourceSenseId, String relType, String targetSenseId,
                                  int line) {
  }

  /**
   * Builds the WN-LMF relation-name to {@link WordNetRelation} table.
   *
   * @return The immutable name table.
   */
  private static Map<String, WordNetRelation> relationNames() {
    final Map<String, WordNetRelation> names = new HashMap<>();
    names.put("antonym", WordNetRelation.ANTONYM);
    names.put("hypernym", WordNetRelation.HYPERNYM);
    names.put("instance_hypernym", WordNetRelation.INSTANCE_HYPERNYM);
    names.put("hyponym", WordNetRelation.HYPONYM);
    names.put("instance_hyponym", WordNetRelation.INSTANCE_HYPONYM);
    names.put("holo_member", WordNetRelation.MEMBER_HOLONYM);
    names.put("holo_substance", WordNetRelation.SUBSTANCE_HOLONYM);
    names.put("holo_part", WordNetRelation.PART_HOLONYM);
    names.put("mero_member", WordNetRelation.MEMBER_MERONYM);
    names.put("mero_substance", WordNetRelation.SUBSTANCE_MERONYM);
    names.put("mero_part", WordNetRelation.PART_MERONYM);
    names.put("attribute", WordNetRelation.ATTRIBUTE);
    names.put("derivation", WordNetRelation.DERIVATIONALLY_RELATED);
    names.put("entails", WordNetRelation.ENTAILMENT);
    names.put("is_entailed_by", WordNetRelation.ENTAILED_BY);
    names.put("causes", WordNetRelation.CAUSE);
    names.put("is_caused_by", WordNetRelation.CAUSED_BY);
    names.put("also", WordNetRelation.ALSO_SEE);
    names.put("participle", WordNetRelation.PARTICIPLE);
    names.put("pertainym", WordNetRelation.PERTAINYM);
    names.put("domain_topic", WordNetRelation.DOMAIN_TOPIC);
    names.put("has_domain_topic", WordNetRelation.MEMBER_OF_DOMAIN_TOPIC);
    names.put("domain_region", WordNetRelation.DOMAIN_REGION);
    names.put("has_domain_region", WordNetRelation.MEMBER_OF_DOMAIN_REGION);
    // The usage domain carries both its current WN-LMF name and the legacy alias.
    names.put("exemplifies", WordNetRelation.DOMAIN_USAGE);
    names.put("domain_usage", WordNetRelation.DOMAIN_USAGE);
    names.put("is_exemplified_by", WordNetRelation.MEMBER_OF_DOMAIN_USAGE);
    names.put("has_domain_usage", WordNetRelation.MEMBER_OF_DOMAIN_USAGE);
    return Map.copyOf(names);
  }
}
