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

package opennlp.tools.formats.ad;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import opennlp.tools.commons.Internal;
import opennlp.tools.formats.ad.ADSentenceStream.Sentence;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.Leaf;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.Node;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.TreeElement;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;

/**
 * Parser for Floresta Sita(c)tica Arvores Deitadas corpus, output to for the
 * Portuguese NER training.
 * <p>
 * The data contains four named entity types: Person, Organization, Group,
 * Place, Event, ArtProd, Abstract, Thing, Time and Numeric.<br>
 * <p>
 * Data can be found on
 * <a href="http://www.linguateca.pt/floresta/corpus.html">this web site</a>.
 *
 * <p>
 * Information about the format:<br>
 * Susana Afonso.
 * <a href="http://www.linguateca.pt/documentos/Afonso2006ArvoresDeitadas.pdf">
 *   "Árvores deitadas: Descrição do formato e das opções de análise na Floresta Sintáctica"</a>.
 * <br>
 * 12 de Fevereiro de 2006.
 * <p>
 * Detailed info about the
 * <a href="http://beta.visl.sdu.dk/visl/pt/info/portsymbol.html#semtags_names">NER tagset</a>.
 * <p>
 * Whitespace inside the tags of a leaf and in a contraction is the Unicode White_Space property,
 * see {@link StringUtil#isUnicodeWhitespace(char)}, independent of the whitespace mode.
 * <p>
 * <b>Note:</b>
 * Do not use this class, internal use only!
 */
@Internal
public class ADNameSampleStream implements ObjectStream<NameSample> {

  /*
   * Map to the Arvores Deitadas types to our types. It is read-only.
   */
  private static final Map<String, String> HAREM;

  private static final String NER_PREFIX = "NER:";
  private static final String HYPHEN = "-";
  private static final char HYPHEN_CHAR = '-';
  private static final char UNDERSCORE = '_';
  private static final char TAG_OPEN = '<';
  private static final char TAG_CLOSE = '>';
  private static final String LITERARY_PREFIX = "LIT";
  private static final String SCIENTIFIC_PREFIX = "CIE";
  private static final String INVALID_METADATA = "Invalid metadata: ";

  static {
    Map<String, String> harem = new HashMap<>();

    final String person = "person";
    harem.put("hum", person);
    harem.put("official", person);
    harem.put("member", person);

    final String organization = "organization";
    harem.put("admin", organization);
    harem.put("org", organization);
    harem.put("inst", organization);
    harem.put("media", organization);
    harem.put("party", organization);
    harem.put("suborg", organization);

    final String group = "group";
    harem.put("groupind", group);
    harem.put("groupofficial", group);

    final String place = "place";
    harem.put("top", place);
    harem.put("civ", place);
    harem.put("address", place);
    harem.put("site", place);
    harem.put("virtual", place);
    harem.put("astro", place);

    final String event = "event";
    harem.put("occ", event);
    harem.put("event", event);
    harem.put("history", event);

    final String artprod = "artprod";
    harem.put("tit", artprod);
    harem.put("pub", artprod);
    harem.put("product", artprod);
    harem.put("V", artprod);
    harem.put("artwork", artprod);

    final String _abstract = "abstract";
    harem.put("brand", _abstract);
    harem.put("genre", _abstract);
    harem.put("school", _abstract);
    harem.put("idea", _abstract);
    harem.put("plan", _abstract);
    harem.put("author", _abstract);
    harem.put("absname", _abstract);
    harem.put("disease", _abstract);

    final String thing = "thing";
    harem.put("object", thing);
    harem.put("common", thing);
    harem.put("mat", thing);
    harem.put("class", thing);
    harem.put("plant", thing);
    harem.put("currency", thing);

    final String time = "time";
    harem.put("date", time);
    harem.put("hour", time);
    harem.put("period", time);
    harem.put("cyclic", time);

    final String numeric = "numeric";
    harem.put("quantity", numeric);
    harem.put("prednum", numeric);
    harem.put("currency", numeric);

    HAREM = Collections.unmodifiableMap(harem);
  }

  private final ObjectStream<ADSentenceStream.Sentence> adSentenceStream;

  /*
   * To keep the last left contraction part
   */
  private String leftContractionPart = null;

  private final boolean splitHyphenatedTokens;

  /**
   * Initializes a new {@link ADNameSampleStream} stream from a {@link ObjectStream<String>},
   * that could be a {@link PlainTextByLineStream} object.
   *
   * @param lineStream An {@link ObjectStream<String>} as input.
   * @param splitHyphenatedTokens If {@code true} hyphenated tokens will be separated:
   *                              "carros-monstro" &gt; "carros" "-" "monstro".
   *                              Combining marks remain attached to their letters. If
   *                              {@code false}, hyphenated words remain single tokens.
   */
  public ADNameSampleStream(ObjectStream<String> lineStream, boolean splitHyphenatedTokens) {
    this.adSentenceStream = new ADSentenceStream(lineStream);
    this.splitHyphenatedTokens = splitHyphenatedTokens;
  }

  /**
   * Initializes a new {@link ADNameSampleStream} from an {@link InputStreamFactory}
   *
   * @param in The Corpus {@link InputStreamFactory}.
   * @param charsetName  The {@link java.nio.charset.Charset charset} to use
   *                     for reading of the corpus.
   * @param splitHyphenatedTokens If {@code true} hyphenated tokens will be separated:
   *                              "carros-monstro" &gt; "carros" "-" "monstro".
   *                              Combining marks remain attached to their letters. If
   *                              {@code false}, hyphenated words remain single tokens.
   */
  @Deprecated(forRemoval = true)
  public ADNameSampleStream(InputStreamFactory in, String charsetName,
      boolean splitHyphenatedTokens) throws IOException {
    this(new PlainTextByLineStream(in, charsetName), splitHyphenatedTokens);
  }

  private int textID = -1;

  /** The number of distinct LIT or CIE texts read so far, minus one. */
  private int textIdMeta2 = -1;

  /** The LIT or CIE text name of the sentence read last. */
  private String textMeta2 = "";

  @Override
  public NameSample read() throws IOException {

    Sentence paragraph;
    // we should look for text here.
    if ((paragraph = this.adSentenceStream.read()) != null) {

      int currentTextID = getTextID(paragraph);
      boolean clearData = false;
      if (currentTextID != textID) {
        clearData = true;
        textID = currentTextID;
      }

      Node root = paragraph.root();
      List<String> sentence = new ArrayList<>();
      List<Span> names = new ArrayList<>();
      process(root, sentence, names);

      return new NameSample(sentence.toArray(new String[0]),
          names.toArray(new Span[0]), clearData);
    }
    return null;
  }

  /**
   * Recursive method to process a {@link Node} in Arvores Deitadas format.
   *
   * @param node The {@link Node} to be processed.
   * @param sentence The {@link List<String> sentence tokens} processed so far.
   * @param names The {@link List<Span> names} processed so far.
   */
  private void process(Node node, List<String> sentence, List<Span> names) {
    if (node != null) {
      for (TreeElement element : node.getElements()) {
        if (element.isLeaf()) {
          processLeaf((Leaf) element, sentence, names);
        } else {
          process((Node) element, sentence, names);
        }
      }
    }
  }

  /**
   * Processes a {@link Leaf} of Arvores Detaitadas format
   *
   * @param leaf The {@link Leaf} to be processed
   * @param sentence The {@link List<String> sentence tokens} processed so far.
   * @param names The {@link List<Span> names} processed so far.
   */
  private void processLeaf(Leaf leaf, List<String> sentence, List<Span> names) {

    boolean alreadyAdded = false;

    if (leftContractionPart != null) {
      // will handle the contraction
      String right = leaf.getLexeme();

      String c = PortugueseContractionUtility.toContraction(
          leftContractionPart, right);
      if (c != null) {
        String[] parts = StringUtil.splitOnUnicodeWhitespace(c);
        sentence.addAll(Arrays.asList(parts));
        alreadyAdded = true;
      } else {
        // contraction was missing! why?
        sentence.add(leftContractionPart);
        // keep alreadyAdded false.
      }
      leftContractionPart = null;
    }

    String namedEntityTag = null;
    int startOfNamedEntity = -1;

    String leafTag = leaf.getSecondaryTag();
    boolean expandLastNER = false; // used when we find a <NER2> tag

    if (leafTag != null) {
      if (leafTag.contains("<sam->") && !alreadyAdded) {
        String[] lexemes = splitOnUnderscores(leaf.getLexeme());
        if (lexemes.length > 1) {
          sentence.addAll(Arrays.asList(lexemes).subList(0, lexemes.length - 1));
        }
        leftContractionPart = lexemes.length == 0 ? null : lexemes[lexemes.length - 1];
        return;
      }
      if (leafTag.contains("<NER2>")) {
        // this one an be part of the last name
        expandLastNER = true;
      }
      namedEntityTag = getNER(leafTag);
    }

    if (namedEntityTag != null) {
      startOfNamedEntity = sentence.size();
    }

    if (!alreadyAdded) {
      sentence.addAll(processLexeme(leaf.getLexeme()));
    }

    if (namedEntityTag != null) {
      names
      .add(new Span(startOfNamedEntity, sentence.size(), namedEntityTag));
    }

    if (expandLastNER) {
      // if the current leaf has the tag <NER2>, it can be the continuation of
      // a NER.
      // we check if it is true, and expand the last NER
      int lastIndex = names.size() - 1;
      if (names.size() > 0) {
        Span last = names.get(lastIndex);
        if (last.getEnd() == sentence.size() - 1) {
          names.set(lastIndex, new Span(last.getStart(), sentence.size(),
              last.getType()));
        }
      }
    }

  }

  private List<String> processLexeme(String lexemeStr) {
    List<String> out = new ArrayList<>();
    String[] parts = splitOnUnderscores(lexemeStr);
    for (String tok : parts) {
      if (tok.length() > 1 && !isAlphaNumeric(tok)) {
        out.addAll(processTok(tok));
      } else {
        out.add(tok);
      }
    }
    return out;
  }

  private List<String> processTok(String tok) {
    boolean tokAdded = false;
    String original = tok;
    List<String> out = new ArrayList<>();
    LinkedList<String> suffix = new LinkedList<>();
    char first = tok.charAt(0);
    if (first == '«') {
      out.add(Character.toString(first));
      tok = tok.substring(1);
    }
    char last = tok.charAt(tok.length() - 1);
    if (last == '»' || last == ':' || last == ',' || last == '!' ) {
      suffix.add(Character.toString(last));
      tok = tok.substring(0, tok.length() - 1);
    }

    // lets split all hyphens
    if (this.splitHyphenatedTokens && tok.contains(HYPHEN) && tok.length() > 1) {
      String[] parts = matchHyphenatedToken(tok);

      if (parts != null) {
        addIfNotEmpty(parts[0], out);
        addIfNotEmpty(HYPHEN, out);
        addIfNotEmpty(parts[1], out);
        addIfNotEmpty(parts[2], out);
        tokAdded = true;
      }
    }
    if (!tokAdded) {
      if (!original.equals(tok) && tok.length() > 1
          && !isAlphaNumeric(tok)) {
        out.addAll(processTok(tok));
      } else {
        out.add(tok);
      }
    }
    out.addAll(suffix);
    return out;
  }

  private void addIfNotEmpty(String firstTok, List<String> out) {
    if (firstTok != null && firstTok.length() > 0) {
      out.addAll(processTok(firstTok));
    }
  }

  /**
   * Splits a lexeme on underscores into its non-empty parts, so a lexeme such as
   * {@code Rio_de_Janeiro} yields its three words. Runs of underscores count as one separator,
   * and leading or trailing underscores add no part.
   *
   * @param s The lexeme.
   * @return The parts in order; empty when the lexeme has no character other than underscores.
   */
  String[] splitOnUnderscores(String s) {
    if (s.isEmpty()) {
      return new String[0];
    }
    if (s.indexOf(UNDERSCORE) == -1) {
      return new String[] {s};
    }
    List<String> tokens = new ArrayList<>();
    int start = -1;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == UNDERSCORE) {
        if (start >= 0) {
          tokens.add(s.substring(start, i));
          start = -1;
        }
      } else if (start < 0) {
        start = i;
      }
    }
    if (start >= 0) {
      tokens.add(s.substring(start));
    }
    return tokens.toArray(new String[0]);
  }

  /**
   * Tests whether a token consists of letters and decimal digits only, by code point.
   *
   * @param tok The token.
   * @return {@code true} if the token is non-empty and every code point is a letter or a
   *         decimal digit.
   */
  boolean isAlphaNumeric(String tok) {
    if (tok.isEmpty()) {
      return false;
    }
    int i = 0;
    while (i < tok.length()) {
      int cp = tok.codePointAt(i);
      if (!Character.isLetter(cp) && !Character.isDigit(cp)) {
        return false;
      }
      i += Character.charCount(cp);
    }
    return true;
  }

  /**
   * Splits a hyphenated token into the letters before the hyphen, the letters after it, and
   * the rest. Three shapes match: letters followed by a final hyphen, a leading hyphen followed
   * by letters and anything, and letters, a hyphen, letters, and anything.
   * Each letter run includes its combining marks without normalizing the original text.
   *
   * @param tok The token, at least two characters long.
   * @return The first token, second token, and rest, each {@code null} when absent, or
   *         {@code null} if the token has none of the three shapes.
   */
  String[] matchHyphenatedToken(String tok) {
    int len = tok.length();
    if (len > 1 && tok.charAt(len - 1) == HYPHEN_CHAR && lettersEnd(tok, 0) == len - 1) {
      return new String[] {tok.substring(0, len - 1), null, null};
    }
    if (tok.charAt(0) == HYPHEN_CHAR) {
      int lettersEnd = lettersEnd(tok, 1);
      if (lettersEnd > 1) {
        return new String[] {null, tok.substring(1, lettersEnd), tok.substring(lettersEnd)};
      }
      return null;
    }
    int firstEnd = lettersEnd(tok, 0);
    if (firstEnd > 0 && firstEnd + 1 < len && tok.charAt(firstEnd) == HYPHEN_CHAR) {
      int secondEnd = lettersEnd(tok, firstEnd + 1);
      if (secondEnd > firstEnd + 1) {
        return new String[] {tok.substring(0, firstEnd),
            tok.substring(firstEnd + 1, secondEnd), tok.substring(secondEnd)};
      }
    }
    return null;
  }

  /**
   * Finds the end of a run starting with a letter and continuing with letters or combining
   * marks. A mark cannot start a run; marks following a letter stay attached to it.
   *
   * @param s The text.
   * @param from The start offset.
   * @return The offset after the run, or {@code from} if no letter starts there.
   */
  private int lettersEnd(String s, int from) {
    if (from >= s.length() || !Character.isLetter(s.codePointAt(from))) {
      return from;
    }
    int i = from + Character.charCount(s.codePointAt(from));
    while (i < s.length()) {
      int cp = s.codePointAt(i);
      int type = Character.getType(cp);
      if (!Character.isLetter(cp) && type != Character.NON_SPACING_MARK
          && type != Character.COMBINING_SPACING_MARK && type != Character.ENCLOSING_MARK) {
        break;
      }
      i += Character.charCount(cp);
    }
    return i;
  }

  /**
   * Extracts the content of a NER tag in Arvores Deitadas format, between the optional
   * {@code NER:} prefix and the closing angle bracket.
   *
   * @param t The tag.
   * @return The content, or {@code null} if {@code t} is not enclosed in angle brackets.
   */
  String tagContent(String t) {
    if (t.length() < 2 || t.charAt(0) != TAG_OPEN || t.charAt(t.length() - 1) != TAG_CLOSE) {
      return null;
    }
    int start = t.startsWith(NER_PREFIX, 1) ? 1 + NER_PREFIX.length() : 1;
    return t.substring(start, t.length() - 1);
  }

  /**
   * Parses a NER tag in Arvores Deitadas format.
   *
   * @param tags The NER tag in Arvores Deitadas format.
   * @return The NER tag, or {@code null} if not a NER tag in Arvores Deitadas format.
   */
  private String getNER(String tags) {
    if (tags.contains("<NER2>")) {
      return null;
    }
    String[] tag = StringUtil.splitOnUnicodeWhitespace(tags);
    for (String t : tag) {
      String ner = tagContent(t);
      if (ner != null && HAREM.containsKey(ner)) {
        return HAREM.get(ner);
      }
    }
    return null;
  }

  /**
   * {@inheritDoc}
   * Starts a new pass with no preceding text, so its first sample clears adaptive data.
   */
  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    adSentenceStream.reset();
    textID = -1;
    textIdMeta2 = -1;
    textMeta2 = "";
  }

  @Override
  public void close() throws IOException {
    adSentenceStream.close();
  }

  /**
   * Reads the id of the text a sentence belongs to; adaptive data is cleared when it changes. In
   * the Amazonia corpus it is the text id of the metadata. In the literary and scientific corpora
   * the text is named by its reference prefix or its source attribute instead, and the id counts
   * the distinct names seen so far, so it changes when a new text starts (OPENNLP-1951).
   *
   * @param paragraph The sentence.
   * @return The id.
   * @throws RuntimeException If the metadata has no id or one that does not fit into an
   *                          {@code int}.
   */
  private int getTextID(Sentence paragraph) {
    final String meta = paragraph.metadata();
    boolean literary = meta.startsWith(LITERARY_PREFIX);
    if (literary || meta.startsWith(SCIENTIFIC_PREFIX)) {
      String textName = literary ? ADMetadata.textPrefix(meta) : ADMetadata.source(meta);
      if (textName == null) {
        throw new RuntimeException(INVALID_METADATA + meta);
      }
      if (textName.isEmpty()) {
        return -1;
      }
      if (!textName.equals(textMeta2)) {
        textIdMeta2++;
        textMeta2 = textName;
      }
      return textIdMeta2;
    }
    ADMetadata.TextAndParagraph ids = ADMetadata.parseTextAndParagraph(meta);
    if (ids == null) {
      throw new RuntimeException(INVALID_METADATA + meta);
    }
    return ids.text();
  }

}
