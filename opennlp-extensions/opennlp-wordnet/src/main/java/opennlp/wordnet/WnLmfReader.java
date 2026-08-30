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
import javax.xml.namespace.QName;
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
 * <p>It reads lexical entries, synsets with their definitions and every typed relation in WN-LMF
 * 1.4, and sense relations, which are lifted to the synset level as documented on
 * {@link WordNetRelation}. Elements outside that subset are skipped, as are relations of type
 * {@code other}, the format's untyped escape hatch. Any other unknown relation type fails loud.</p>
 *
 * <p>The parser is hardened against XXE: DTD processing and external entities are disabled, so a
 * DOCTYPE is skipped but nothing it names is fetched or resolved.</p>
 *
 * <p>Malformed structure fails loud with an {@link InvalidFormatException} naming the resource
 * and, where the parser provides one, the line; I/O failures propagate as {@link IOException}.
 * Part-of-speech code {@code s} normalizes to {@link WordNetPOS#ADJECTIVE}, and a {@code similar}
 * relation on a verb synset maps to {@link WordNetRelation#VERB_GROUP} rather than
 * {@link WordNetRelation#SIMILAR_TO}. Use {@link #readResource(Path)} when a document contains
 * several lexicons; the single-lexicon {@code read} methods reject that shape instead of merging
 * language-specific indexes. WN-LMF {@code Requires} declarations are preserved as dependency
 * metadata but never resolved or loaded. Returned resources and lexicons are immutable and safe
 * for concurrent lookups.</p>
 */
public final class WnLmfReader {

  /** The WN-LMF relation names this reader accepts, mapped to the contract relations. */
  private static final Map<String, WordNetRelation> RELATION_NAMES = relationNames();

  /** Relations declared only for SynsetRelation in WN-LMF 1.4. */
  private static final Set<String> SYNSET_ONLY_RELATIONS = Set.of(
      "attribute", "be_in_state", "causes", "classified_by", "classifies",
      "co_agent_instrument", "co_agent_patient", "co_agent_result", "co_instrument_agent",
      "co_instrument_patient", "co_instrument_result", "co_patient_agent",
      "co_patient_instrument", "co_result_agent", "co_result_instrument", "co_role",
      "direction", "entails", "eq_synonym", "holo_location", "holo_member", "holo_part",
      "holo_portion", "holo_substance", "holonym", "hypernym", "hyponym", "in_manner",
      "instance_hypernym", "instance_hyponym", "involved", "involved_agent",
      "involved_direction", "involved_instrument", "involved_location", "involved_patient",
      "involved_result", "involved_source_direction", "involved_target_direction",
      "ir_synonym", "is_caused_by", "is_entailed_by", "is_subevent_of", "manner_of",
      "mero_location", "mero_member", "mero_part", "mero_portion", "mero_substance",
      "meronym", "patient", "restricted_by", "restricts", "role", "source_direction",
      "state_of", "subevent", "target_direction");

  /** Relations declared only for SenseRelation in WN-LMF 1.4. */
  private static final Set<String> SENSE_ONLY_RELATIONS = Set.of(
      "body_part", "by_means_of", "derivation", "destination", "event", "has_metaphor",
      "has_metonym", "material", "metaphor", "metonym", "participle", "pertainym",
      "property", "secondary_aspect_ip", "secondary_aspect_pi", "simple_aspect_ip",
      "simple_aspect_pi", "state", "undergoer", "uses", "vehicle");

  /** The format's escape-hatch relation type; carries no type the contract can express. */
  private static final String OTHER_RELATION = "other";

  /** The element declaring a lexical entry; opened and closed by the same handlers. */
  private static final String LEXICAL_ENTRY_ELEMENT = "LexicalEntry";

  /** The element declaring a sense; opened and closed by the same handlers. */
  private static final String SENSE_ELEMENT = "Sense";

  /** The element declaring a synset; opened and closed by the same handlers. */
  private static final String SYNSET_ELEMENT = "Synset";

  /** The element declaring one independently queryable lexicon. */
  private static final String LEXICON_ELEMENT = "Lexicon";

  /** The resource extension form, not represented by the current knowledge-base contract. */
  private static final String LEXICON_EXTENSION_ELEMENT = "LexiconExtension";

  /** The element declaring a lexicon dependency. */
  private static final String REQUIRES_ELEMENT = "Requires";

  /** The identifier attribute shared by entries, senses, and synsets. */
  private static final String ID_ATTRIBUTE = "id";

  /** The human-readable Lexicon label attribute. */
  private static final String LABEL_ATTRIBUTE = "label";

  /** The BCP 47 Lexicon language attribute. */
  private static final String LANGUAGE_ATTRIBUTE = "language";

  /** The Lexicon version attribute. */
  private static final String VERSION_ATTRIBUTE = "version";

  /** The required lexicon reference attribute. */
  private static final String REF_ATTRIBUTE = "ref";

  /** The part-of-speech attribute shared by lemmas and synsets. */
  private static final String PART_OF_SPEECH_ATTRIBUTE = "partOfSpeech";

  /** The relation-type attribute shared by sense and synset relations. */
  private static final String REL_TYPE_ATTRIBUTE = "relType";

  /** The relation-target attribute shared by sense and synset relations. */
  private static final String TARGET_ATTRIBUTE = "target";

  /** The opening of every malformed-document message, before the resource name. */
  private static final String MALFORMED_PREFIX = "Malformed WN-LMF document ";

  /** Not instantiable. */
  private WnLmfReader() {
  }

  /**
   * Reads a WN-LMF XML file.
   *
   * @param file The XML file. Must not be {@code null} and must exist.
   * @return The loaded lexicon.
   * @throws IllegalArgumentException Thrown if {@code file} is {@code null} or missing.
   * @throws InvalidFormatException Thrown if the document is malformed, contains an unsupported
   *     {@code LexiconExtension}, or contains more than one lexicon. The message names the file
   *     and, where available, the line.
   * @throws IOException Thrown if reading the file fails.
   */
  public static LexicalKnowledgeBase read(Path file) throws IOException {
    final WnLmfResource resource = readResource(file);
    return onlyLexicon(resource, file.toString());
  }

  /**
   * Reads every lexicon in a WN-LMF XML file without merging their lookup indexes.
   *
   * @param file The XML file. Must not be {@code null} and must exist.
   * @return The lexical resource in document order.
   * @throws IllegalArgumentException Thrown if {@code file} is {@code null} or missing.
   * @throws InvalidFormatException Thrown if the document is malformed or contains an unsupported
   *     {@code LexiconExtension}.
   * @throws IOException Thrown if reading the file fails.
   */
  public static WnLmfResource readResource(Path file) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("File must not be null");
    }
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("File does not exist or is not a regular file: " + file);
    }
    try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
      return readResource(in, file.toString());
    }
  }

  /**
   * Reads a WN-LMF XML document from a stream. The stream is not closed.
   *
   * @param in           The document stream. Must not be {@code null}.
   * @param resourceName The name used in error messages. Must not be {@code null}.
   * @return The loaded lexicon.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}.
   * @throws InvalidFormatException Thrown if the document is malformed, contains an unsupported
   *     {@code LexiconExtension}, or contains more than one lexicon. The message names the
   *     resource and, where available, the line.
   * @throws IOException Thrown if reading the stream fails.
   */
  public static LexicalKnowledgeBase read(InputStream in, String resourceName) throws IOException {
    final WnLmfResource resource = readResource(in, resourceName);
    return onlyLexicon(resource, resourceName);
  }

  /**
   * Reads every lexicon in a WN-LMF document without merging their lookup indexes. The stream is
   * not closed.
   *
   * @param in           The document stream. Must not be {@code null}.
   * @param resourceName The name used in error messages. Must not be {@code null}.
   * @return The lexical resource in document order.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}.
   * @throws InvalidFormatException Thrown if the document is malformed or contains an unsupported
   *     {@code LexiconExtension}.
   * @throws IOException Thrown if reading the stream fails.
   */
  public static WnLmfResource readResource(InputStream in, String resourceName) throws IOException {
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
    return parser.resource();
  }

  /**
   * Returns the only knowledge base in a resource.
   *
   * @param resource     The parsed resource.
   * @param resourceName The resource name used in the rejection message.
   * @return The resource's only knowledge base.
   * @throws InvalidFormatException Thrown if the resource contains more than one lexicon.
   */
  private static LexicalKnowledgeBase onlyLexicon(WnLmfResource resource, String resourceName)
      throws InvalidFormatException {
    if (resource.lexicons().size() != 1) {
      throw new InvalidFormatException("WN-LMF resource " + resourceName + " contains "
          + resource.lexicons().size() + " lexicons; use WnLmfReader.readResource to preserve "
          + "their boundaries");
    }
    return resource.lexicons().get(0).knowledgeBase();
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
    private final List<WnLmfLexicon> lexicons = new ArrayList<>();
    private final Set<String> lexiconIds = new HashSet<>();
    private final Set<String> documentIds = new HashSet<>();

    // Current Lexicon metadata.
    private String currentLexiconId;
    private String currentLexiconLabel;
    private String currentLexiconLanguage;
    private String currentLexiconVersion;
    private Map<QName, String> currentLexiconMetadata;
    private final List<WnLmfDependency> currentDependencies = new ArrayList<>();

    // Entry state.
    private final Set<String> entryIds = new HashSet<>();
    private final Map<String, String> lemmaByEntryId = new HashMap<>();
    private final Map<String, WordNetPOS> posByEntryId = new HashMap<>();
    private final Map<String, String> synsetBySenseId = new HashMap<>();
    private final Map<String, String> entryIdBySenseId = new HashMap<>();
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
      if (currentLexiconId != null) {
        throw malformed(reader.getLocation(), "Unclosed Lexicon " + currentLexiconId, null);
      }
      if (lexicons.isEmpty()) {
        throw malformed(reader.getLocation(), "Document contains no Lexicon", null);
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
        case LEXICON_ELEMENT -> openLexicon(reader);
        case LEXICON_EXTENSION_ELEMENT -> throw malformed(reader.getLocation(),
            "LexiconExtension is not supported by WnLmfReader", null);
        case REQUIRES_ELEMENT -> {
          requireLexicon(reader, REQUIRES_ELEMENT);
          currentDependencies.add(new WnLmfDependency(
              requireAttribute(reader, REF_ATTRIBUTE),
              requireAttribute(reader, VERSION_ATTRIBUTE)));
        }
        case LEXICAL_ENTRY_ELEMENT -> {
          requireLexicon(reader, LEXICAL_ENTRY_ELEMENT);
          currentEntryId = requireAttribute(reader, ID_ATTRIBUTE);
          if (!entryIds.add(currentEntryId)) {
            throw malformed(reader.getLocation(),
                "Duplicate lexical entry id " + currentEntryId, null);
          }
          claimDocumentId(currentEntryId, "lexical entry", reader.getLocation());
          currentEntryLemma = null;
          currentEntryPos = null;
        }
        case "Lemma" -> {
          if (currentEntryId == null) {
            throw malformed(reader.getLocation(), "Lemma outside a LexicalEntry", null);
          }
          currentEntryLemma = requireAttribute(reader, "writtenForm");
          currentEntryPos = parsePos(requireAttribute(reader, PART_OF_SPEECH_ATTRIBUTE),
              reader.getLocation());
          lemmaByEntryId.put(currentEntryId, currentEntryLemma);
          posByEntryId.put(currentEntryId, currentEntryPos);
        }
        case SENSE_ELEMENT -> {
          if (currentEntryLemma == null) {
            throw malformed(reader.getLocation(),
                "Sense before its entry's Lemma in LexicalEntry " + currentEntryId, null);
          }
          currentSenseId = requireAttribute(reader, ID_ATTRIBUTE);
          final String synsetId = requireAttribute(reader, "synset");
          if (synsetBySenseId.putIfAbsent(currentSenseId, synsetId) != null) {
            throw malformed(reader.getLocation(), "Duplicate sense id " + currentSenseId, null);
          }
          claimDocumentId(currentSenseId, "sense", reader.getLocation());
          entryIdBySenseId.put(currentSenseId, currentEntryId);
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
              requireAttribute(reader, REL_TYPE_ATTRIBUTE),
              requireAttribute(reader, TARGET_ATTRIBUTE), line(reader.getLocation())));
        }
        case SYNSET_ELEMENT -> {
          requireLexicon(reader, SYNSET_ELEMENT);
          final String id = requireAttribute(reader, ID_ATTRIBUTE);
          final WordNetPOS pos = parsePos(requireAttribute(reader, PART_OF_SPEECH_ATTRIBUTE),
              reader.getLocation());
          currentSynset = new RawSynset(id, pos, reader.getAttributeValue(null, "members"),
              line(reader.getLocation()));
          if (rawSynsets.putIfAbsent(id, currentSynset) != null) {
            throw malformed(reader.getLocation(), "Duplicate synset id " + id, null);
          }
          claimDocumentId(id, "synset", reader.getLocation());
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
          final String relType = requireAttribute(reader, REL_TYPE_ATTRIBUTE);
          final String target = requireAttribute(reader, TARGET_ATTRIBUTE);
          // The escape-hatch type is a documented skip, not a rejection.
          if (!OTHER_RELATION.equals(relType)) {
            currentSynset.relations.add(
                new RawRelation(relType, target, line(reader.getLocation()), false));
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
     * @throws InvalidFormatException Thrown if closing a Lexicon exposes invalid content.
     */
    private void endElement(String name) throws InvalidFormatException {
      switch (name) {
        case LEXICON_ELEMENT -> closeLexicon();
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
     * Opens one Lexicon and captures its identity and metadata.
     *
     * @param reader The reader positioned on the Lexicon start element.
     * @throws InvalidFormatException Thrown if the Lexicon is nested, repeats an id, or omits a
     *     required attribute.
     */
    private void openLexicon(XMLStreamReader reader) throws InvalidFormatException {
      if (currentLexiconId != null) {
        throw malformed(reader.getLocation(), "Nested Lexicon inside " + currentLexiconId, null);
      }
      final String id = requireAttribute(reader, ID_ATTRIBUTE);
      if (!lexiconIds.add(id)) {
        throw malformed(reader.getLocation(), "Duplicate lexicon id " + id, null);
      }
      claimDocumentId(id, "lexicon", reader.getLocation());
      currentLexiconId = id;
      currentLexiconLabel = requireAttribute(reader, LABEL_ATTRIBUTE);
      currentLexiconLanguage = requireAttribute(reader, LANGUAGE_ATTRIBUTE);
      currentLexiconVersion = requireAttribute(reader, VERSION_ATTRIBUTE);
      final Map<QName, String> metadata = new LinkedHashMap<>();
      for (int i = 0; i < reader.getAttributeCount(); i++) {
        final QName attribute = reader.getAttributeName(i);
        if (!isIdentityAttribute(attribute)) {
          metadata.put(attribute, reader.getAttributeValue(i));
        }
      }
      currentLexiconMetadata = Map.copyOf(metadata);
    }

    /**
     * Builds the current Lexicon and clears its parse state.
     *
     * @throws InvalidFormatException Thrown if the Lexicon content is invalid.
     */
    private void closeLexicon() throws InvalidFormatException {
      if (currentLexiconId == null) {
        return;
      }
      try {
        lexicons.add(new WnLmfLexicon(currentLexiconId, currentLexiconLabel,
            currentLexiconLanguage, currentLexiconVersion, currentLexiconMetadata,
            currentDependencies, buildKnowledgeBase()));
      } finally {
        clearLexiconState();
      }
    }

    /**
     * Requires lexical content to be enclosed by a Lexicon element.
     *
     * @param reader  The reader positioned on the content element.
     * @param element The element name used in the rejection message.
     * @throws InvalidFormatException Thrown if no Lexicon is open.
     */
    private void requireLexicon(XMLStreamReader reader, String element)
        throws InvalidFormatException {
      if (currentLexiconId == null) {
        throw malformed(reader.getLocation(), element + " outside a Lexicon", null);
      }
    }

    /**
     * Claims an XML ID across the complete LexicalResource.
     *
     * @param id       The identifier to claim.
     * @param kind     The element kind used in a duplicate error.
     * @param location The source location.
     * @throws InvalidFormatException Thrown if another parsed element already carries the id.
     */
    private void claimDocumentId(String id, String kind, Location location)
        throws InvalidFormatException {
      if (!documentIds.add(id)) {
        throw malformed(location, "Duplicate " + kind + " id " + id, null);
      }
    }

    /**
     * Tests whether an attribute is exposed directly on {@link WnLmfLexicon}.
     *
     * @param attribute The attribute name.
     * @return {@code true} for an unqualified id, label, language, or version attribute.
     */
    private boolean isIdentityAttribute(QName attribute) {
      if (!attribute.getNamespaceURI().isEmpty()) {
        return false;
      }
      return switch (attribute.getLocalPart()) {
        case ID_ATTRIBUTE, LABEL_ATTRIBUTE, LANGUAGE_ATTRIBUTE, VERSION_ATTRIBUTE -> true;
        default -> false;
      };
    }

    /** Clears every field whose scope is one Lexicon element. */
    private void clearLexiconState() {
      currentLexiconId = null;
      currentLexiconLabel = null;
      currentLexiconLanguage = null;
      currentLexiconVersion = null;
      currentLexiconMetadata = null;
      currentDependencies.clear();
      entryIds.clear();
      lemmaByEntryId.clear();
      posByEntryId.clear();
      synsetBySenseId.clear();
      entryIdBySenseId.clear();
      senseOrder.clear();
      senseRelations.clear();
      rawSynsets.clear();
      entryIdsBySynset.clear();
      currentEntryId = null;
      currentEntryLemma = null;
      currentEntryPos = null;
      currentSenseId = null;
      currentSynset = null;
    }

    /**
     * Resolves the collected raw state into an immutable lexicon: validates sense targets, lifts
     * sense relations to the synset level, and materializes the contract synsets.
     *
     * @return The loaded lexicon.
     * @throws InvalidFormatException Thrown if a sense or relation references an undeclared
     *     target or a declared member is invalid.
     */
    LexicalKnowledgeBase buildKnowledgeBase() throws InvalidFormatException {
      // Every sense must point to a declared synset; part-of-speech consistency between a
      // synset and its member entries is checked in memberLemmas.
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
        source.relations.add(
            new RawRelation(relation.relType, targetSynsetId, relation.line, true));
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
     * Returns the parsed resource.
     *
     * @return The immutable resource in document order.
     */
    WnLmfResource resource() {
      return new WnLmfResource(lexicons);
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
        final WordNetRelation type = parseRelation(
            relation.relType, raw.pos, relation.line, relation.senseRelation);
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
     * Resolves a synset's member sense ids to their entry lemmas, from the {@code members}
     * attribute when present and otherwise from the senses that pointed at the synset. Legacy
     * documents that put lexical-entry ids in {@code members} remain accepted.
     *
     * @param raw The raw synset.
     * @return The member lemmas in source order, deduplicated.
     * @throws InvalidFormatException Thrown if the synset names an undeclared member or a
     *     member's part of speech disagrees with the synset's.
     */
    private List<String> memberLemmas(RawSynset raw) throws InvalidFormatException {
      final List<String> entryIds;
      if (raw.members != null && !raw.members.isEmpty()) {
        entryIds = LemmaFolding.splitOnSpaces(raw.members);
      } else {
        final List<String> fromSenses = entryIdsBySynset.get(raw.id);
        entryIds = fromSenses == null ? List.of() : fromSenses;
      }
      final List<String> lemmas = new ArrayList<>(entryIds.size());
      for (final String memberId : entryIds) {
        final String fromSense = entryIdBySenseId.get(memberId);
        final String entryId = fromSense == null ? memberId : fromSense;
        final String lemma = lemmaByEntryId.get(entryId);
        if (lemma == null) {
          throw malformed(null, "Synset " + raw.id + " at line " + raw.line
              + " lists undeclared member sense or entry " + memberId, null);
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
     * Maps a WN-LMF relation name to a {@link WordNetRelation}, enforcing whether the DTD permits
     * it on a SenseRelation or SynsetRelation. A {@code similar} relation on a verb synset maps to
     * {@link WordNetRelation#VERB_GROUP}, otherwise to {@link WordNetRelation#SIMILAR_TO}.
     *
     * @param relType   The relation name.
     * @param sourcePos The part of speech of the source synset.
     * @param line      The document line, for error reporting.
     * @param senseRelation Whether the relation originated on a Sense.
     * @return The mapped relation.
     * @throws InvalidFormatException Thrown if the relation name is unknown.
     */
    private WordNetRelation parseRelation(String relType, WordNetPOS sourcePos, int line,
                                          boolean senseRelation)
        throws InvalidFormatException {
      if (senseRelation && SYNSET_ONLY_RELATIONS.contains(relType)) {
        throw malformed(null, "Relation type " + relType
            + " is not legal on SenseRelation at line " + line, null);
      }
      if (!senseRelation && SENSE_ONLY_RELATIONS.contains(relType)) {
        throw malformed(null, "Relation type " + relType
            + " is not legal on SynsetRelation at line " + line, null);
      }
      if ("similar".equals(relType)) {
        return !senseRelation && sourcePos == WordNetPOS.VERB ? WordNetRelation.VERB_GROUP
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
    private InvalidFormatException malformed(Location location, String message, Throwable cause) {
      final int line = line(location);
      final String prefix = line < 0 ? MALFORMED_PREFIX + resourceName + ": "
          : MALFORMED_PREFIX + resourceName + " at line " + line + ": ";
      return cause == null ? new InvalidFormatException(prefix + message)
          : new InvalidFormatException(prefix + message, cause);
    }

    /**
     * Extracts a line number from a parser location.
     *
     * @param location The location, or {@code null}.
     * @return The line number, or {@code -1} when unknown.
     */
    private int line(Location location) {
      return location == null ? -1 : location.getLineNumber();
    }
  }

  /** A parsed synset, kept until its members and relation targets can be resolved. */
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
  private record RawRelation(String relType, String target, int line, boolean senseRelation) {
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
    names.put("agent", WordNetRelation.AGENT);
    names.put("also", WordNetRelation.ALSO_SEE);
    names.put("anto_converse", WordNetRelation.ANTO_CONVERSE);
    names.put("anto_gradable", WordNetRelation.ANTO_GRADABLE);
    names.put("anto_simple", WordNetRelation.ANTO_SIMPLE);
    names.put("antonym", WordNetRelation.ANTONYM);
    names.put("attribute", WordNetRelation.ATTRIBUTE);
    names.put("augmentative", WordNetRelation.AUGMENTATIVE);
    names.put("be_in_state", WordNetRelation.BE_IN_STATE);
    names.put("body_part", WordNetRelation.BODY_PART);
    names.put("by_means_of", WordNetRelation.BY_MEANS_OF);
    names.put("causes", WordNetRelation.CAUSE);
    names.put("classified_by", WordNetRelation.CLASSIFIED_BY);
    names.put("classifies", WordNetRelation.CLASSIFIES);
    names.put("co_agent_instrument", WordNetRelation.CO_AGENT_INSTRUMENT);
    names.put("co_agent_patient", WordNetRelation.CO_AGENT_PATIENT);
    names.put("co_agent_result", WordNetRelation.CO_AGENT_RESULT);
    names.put("co_instrument_agent", WordNetRelation.CO_INSTRUMENT_AGENT);
    names.put("co_instrument_patient", WordNetRelation.CO_INSTRUMENT_PATIENT);
    names.put("co_instrument_result", WordNetRelation.CO_INSTRUMENT_RESULT);
    names.put("co_patient_agent", WordNetRelation.CO_PATIENT_AGENT);
    names.put("co_patient_instrument", WordNetRelation.CO_PATIENT_INSTRUMENT);
    names.put("co_result_agent", WordNetRelation.CO_RESULT_AGENT);
    names.put("co_result_instrument", WordNetRelation.CO_RESULT_INSTRUMENT);
    names.put("co_role", WordNetRelation.CO_ROLE);
    names.put("derivation", WordNetRelation.DERIVATIONALLY_RELATED);
    names.put("destination", WordNetRelation.DESTINATION);
    names.put("diminutive", WordNetRelation.DIMINUTIVE);
    names.put("direction", WordNetRelation.DIRECTION);
    names.put("domain_region", WordNetRelation.DOMAIN_REGION);
    names.put("domain_topic", WordNetRelation.DOMAIN_TOPIC);
    names.put("entails", WordNetRelation.ENTAILMENT);
    names.put("eq_synonym", WordNetRelation.EQ_SYNONYM);
    names.put("event", WordNetRelation.EVENT);
    names.put("exemplifies", WordNetRelation.DOMAIN_USAGE);
    names.put("feminine", WordNetRelation.FEMININE);
    names.put("has_augmentative", WordNetRelation.HAS_AUGMENTATIVE);
    names.put("has_diminutive", WordNetRelation.HAS_DIMINUTIVE);
    names.put("has_domain_region", WordNetRelation.MEMBER_OF_DOMAIN_REGION);
    names.put("has_domain_topic", WordNetRelation.MEMBER_OF_DOMAIN_TOPIC);
    names.put("has_feminine", WordNetRelation.HAS_FEMININE);
    names.put("has_masculine", WordNetRelation.HAS_MASCULINE);
    names.put("has_metaphor", WordNetRelation.HAS_METAPHOR);
    names.put("has_metonym", WordNetRelation.HAS_METONYM);
    names.put("has_young", WordNetRelation.HAS_YOUNG);
    names.put("holo_location", WordNetRelation.LOCATION_HOLONYM);
    names.put("holo_member", WordNetRelation.MEMBER_HOLONYM);
    names.put("holo_part", WordNetRelation.PART_HOLONYM);
    names.put("holo_portion", WordNetRelation.PORTION_HOLONYM);
    names.put("holo_substance", WordNetRelation.SUBSTANCE_HOLONYM);
    names.put("holonym", WordNetRelation.HOLONYM);
    names.put("hypernym", WordNetRelation.HYPERNYM);
    names.put("instance_hypernym", WordNetRelation.INSTANCE_HYPERNYM);
    names.put("hyponym", WordNetRelation.HYPONYM);
    names.put("instance_hyponym", WordNetRelation.INSTANCE_HYPONYM);
    names.put("in_manner", WordNetRelation.IN_MANNER);
    names.put("instrument", WordNetRelation.INSTRUMENT);
    names.put("involved", WordNetRelation.INVOLVED);
    names.put("involved_agent", WordNetRelation.INVOLVED_AGENT);
    names.put("involved_direction", WordNetRelation.INVOLVED_DIRECTION);
    names.put("involved_instrument", WordNetRelation.INVOLVED_INSTRUMENT);
    names.put("involved_location", WordNetRelation.INVOLVED_LOCATION);
    names.put("involved_patient", WordNetRelation.INVOLVED_PATIENT);
    names.put("involved_result", WordNetRelation.INVOLVED_RESULT);
    names.put("involved_source_direction", WordNetRelation.INVOLVED_SOURCE_DIRECTION);
    names.put("involved_target_direction", WordNetRelation.INVOLVED_TARGET_DIRECTION);
    names.put("ir_synonym", WordNetRelation.IR_SYNONYM);
    names.put("is_caused_by", WordNetRelation.CAUSED_BY);
    names.put("is_entailed_by", WordNetRelation.ENTAILED_BY);
    names.put("is_exemplified_by", WordNetRelation.MEMBER_OF_DOMAIN_USAGE);
    names.put("is_subevent_of", WordNetRelation.IS_SUBEVENT_OF);
    names.put("location", WordNetRelation.LOCATION);
    names.put("manner_of", WordNetRelation.MANNER_OF);
    names.put("masculine", WordNetRelation.MASCULINE);
    names.put("material", WordNetRelation.MATERIAL);
    names.put("mero_location", WordNetRelation.LOCATION_MERONYM);
    names.put("mero_member", WordNetRelation.MEMBER_MERONYM);
    names.put("mero_part", WordNetRelation.PART_MERONYM);
    names.put("mero_portion", WordNetRelation.PORTION_MERONYM);
    names.put("mero_substance", WordNetRelation.SUBSTANCE_MERONYM);
    names.put("meronym", WordNetRelation.MERONYM);
    names.put("metaphor", WordNetRelation.METAPHOR);
    names.put("metonym", WordNetRelation.METONYM);
    names.put("participle", WordNetRelation.PARTICIPLE);
    names.put("patient", WordNetRelation.PATIENT);
    names.put("pertainym", WordNetRelation.PERTAINYM);
    names.put("property", WordNetRelation.PROPERTY);
    names.put("restricted_by", WordNetRelation.RESTRICTED_BY);
    names.put("restricts", WordNetRelation.RESTRICTS);
    names.put("result", WordNetRelation.RESULT);
    names.put("role", WordNetRelation.ROLE);
    names.put("secondary_aspect_ip", WordNetRelation.SECONDARY_ASPECT_IP);
    names.put("secondary_aspect_pi", WordNetRelation.SECONDARY_ASPECT_PI);
    names.put("simple_aspect_ip", WordNetRelation.SIMPLE_ASPECT_IP);
    names.put("simple_aspect_pi", WordNetRelation.SIMPLE_ASPECT_PI);
    names.put("source_direction", WordNetRelation.SOURCE_DIRECTION);
    names.put("state", WordNetRelation.STATE);
    names.put("state_of", WordNetRelation.STATE_OF);
    names.put("subevent", WordNetRelation.SUBEVENT);
    names.put("target_direction", WordNetRelation.TARGET_DIRECTION);
    names.put("undergoer", WordNetRelation.UNDERGOER);
    names.put("uses", WordNetRelation.USES);
    names.put("vehicle", WordNetRelation.VEHICLE);
    names.put("young", WordNetRelation.YOUNG);
    // Legacy aliases accepted by older WN-LMF producers.
    names.put("domain_usage", WordNetRelation.DOMAIN_USAGE);
    names.put("has_domain_usage", WordNetRelation.MEMBER_OF_DOMAIN_USAGE);
    return Map.copyOf(names);
  }
}
