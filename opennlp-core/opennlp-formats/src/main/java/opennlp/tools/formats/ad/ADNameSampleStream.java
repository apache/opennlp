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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * <b>Note:</b>
 * Do not use this class, internal use only!
 */
@Internal
public class ADNameSampleStream implements ObjectStream<NameSample> {

  /*
   * Map to the Arvores Deitadas types to our types. It is read-only.
   */
  private static final Map<String, String> HAREM;

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
   */
  @Deprecated(forRemoval = true)
  public ADNameSampleStream(InputStreamFactory in, String charsetName,
      boolean splitHyphenatedTokens) throws IOException {
    this(new PlainTextByLineStream(in, charsetName), splitHyphenatedTokens);
  }

  private int textID = -1;

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
        String[] parts = splitOnWhitespace(c);
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
        leftContractionPart = lexemes[lexemes.length - 1];
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
    if (this.splitHyphenatedTokens && tok.contains("-") && tok.length() > 1) {
      String[] parts = matchHyphenatedToken(tok);

      if (parts != null) {
        addIfNotEmpty(parts[0], out);
        addIfNotEmpty("-", out);
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

  /*
   * Replicates String.split("\\s+"): runs of ASCII whitespace collapse, a
   * leading run yields one empty leading field, trailing empty fields are
   * dropped, and an all-whitespace input yields no fields.
   */
  private static String[] splitOnWhitespace(String s) {
    if (s.isEmpty()) {
      return new String[] {""};
    }
    boolean hasToken = false;
    for (int i = 0; i < s.length(); i++) {
      if (!isAsciiWhitespace(s.charAt(i))) {
        hasToken = true;
        break;
      }
    }
    if (!hasToken) {
      return new String[0];
    }
    List<String> tokens = new ArrayList<>();
    if (isAsciiWhitespace(s.charAt(0))) {
      tokens.add("");
    }
    int start = 0;
    for (int i = 0; i < s.length(); i++) {
      if (isAsciiWhitespace(s.charAt(i))) {
        if (i > start) {
          tokens.add(s.substring(start, i));
        }
        while (i + 1 < s.length() && isAsciiWhitespace(s.charAt(i + 1))) {
          i++;
        }
        start = i + 1;
      }
    }
    if (s.length() > start) {
      tokens.add(s.substring(start));
    }
    return tokens.toArray(new String[0]);
  }

  /*
   * Replicates split on [_]+ with the same semantics as splitOnWhitespace.
   */
  private static String[] splitOnUnderscores(String s) {
    if (s.isEmpty()) {
      return new String[] {""};
    }
    boolean hasToken = false;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) != '_') {
        hasToken = true;
        break;
      }
    }
    if (!hasToken) {
      return new String[0];
    }
    List<String> tokens = new ArrayList<>();
    if (s.charAt(0) == '_') {
      tokens.add("");
    }
    int start = 0;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == '_') {
        if (i > start) {
          tokens.add(s.substring(start, i));
        }
        while (i + 1 < s.length() && s.charAt(i + 1) == '_') {
          i++;
        }
        start = i + 1;
      }
    }
    if (s.length() > start) {
      tokens.add(s.substring(start));
    }
    return tokens.toArray(new String[0]);
  }

  /*
   * Replicates matches() of ^[\p{L}\p{Nd}]+$ at code point granularity.
   */
  private static boolean isAlphaNumeric(String tok) {
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

  /*
   * Replicates matches() of the three-branch hyphen pattern
   * ((\p{L}+)-$)|(^-(\p{L}+)(.*))|((\p{L}+)-(\p{L}+)(.*)). Returns the first
   * token, second token, and rest, any of them null, or null when no branch
   * matches.
   */
  private static String[] matchHyphenatedToken(String tok) {
    int len = tok.length();
    // (\p{L}+)-$
    if (tok.charAt(len - 1) == '-' && isAllLetters(tok, 0, len - 1)) {
      return new String[] {tok.substring(0, len - 1), null, null};
    }
    // ^-(\p{L}+)(.*)
    if (tok.charAt(0) == '-') {
      int lettersEnd = lettersEnd(tok, 1);
      if (lettersEnd > 1) {
        return new String[] {null, tok.substring(1, lettersEnd), tok.substring(lettersEnd)};
      }
      return null;
    }
    // (\p{L}+)-(\p{L}+)(.*)
    int firstEnd = lettersEnd(tok, 0);
    if (firstEnd > 0 && firstEnd + 1 < len && tok.charAt(firstEnd) == '-') {
      int secondEnd = lettersEnd(tok, firstEnd + 1);
      if (secondEnd > firstEnd + 1) {
        return new String[] {tok.substring(0, firstEnd),
            tok.substring(firstEnd + 1, secondEnd), tok.substring(secondEnd)};
      }
    }
    return null;
  }

  /**
   * Finds the end of the run of letters starting at an offset.
   *
   * @param s The text.
   * @param from The start offset.
   * @return The offset after the run, or {@code from} if no letter starts there.
   */
  private static int lettersEnd(String s, int from) {
    int i = from;
    while (i < s.length()) {
      int cp = s.codePointAt(i);
      if (!Character.isLetter(cp)) {
        break;
      }
      i += Character.charCount(cp);
    }
    return i;
  }

  /**
   * Tests whether a range holds letters only.
   *
   * @param s The text.
   * @param from The inclusive start.
   * @param to The exclusive end.
   * @return {@code true} if every code point in the range is a letter.
   */
  private static boolean isAllLetters(String s, int from, int to) {
    int i = from;
    while (i < to) {
      int cp = s.codePointAt(i);
      if (!Character.isLetter(cp)) {
        return false;
      }
      i += Character.charCount(cp);
    }
    return true;
  }

  /**
   * Tests for ASCII whitespace: space, tab, line feed, vertical tab, form feed, carriage return.
   *
   * @param c The character.
   * @return {@code true} for one of those six characters.
   */
  private static boolean isAsciiWhitespace(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r';
  }

  /*
   * Replicates matches() of <(NER:)?(.*?)>: the content between the optional
   * NER: prefix and the closing angle bracket, or null when the tag does not
   * match.
   */
  private static String tagContent(String t) {
    if (t.length() < 2 || t.charAt(0) != '<' || t.charAt(t.length() - 1) != '>') {
      return null;
    }
    int start = t.startsWith("NER:", 1) ? 5 : 1;
    return t.substring(start, t.length() - 1);
  }

  /**
   * Parses a NER tag in Arvores Deitadas format.
   *
   * @param tags The NER tag in Arvores Deitadas format.
   * @return The NER tag, or {@code null} if not a NER tag in Arvores Deitadas format.
   */
  private static String getNER(String tags) {
    if (tags.contains("<NER2>")) {
      return null;
    }
    String[] tag = splitOnWhitespace(tags);
    for (String t : tag) {
      String ner = tagContent(t);
      if (ner != null && HAREM.containsKey(ner)) {
        return HAREM.get(ner);
      }
    }
    return null;
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    adSentenceStream.reset();
  }

  @Override
  public void close() throws IOException {
    adSentenceStream.close();
  }

  enum Type {
    ama, cie, lit
  }

  // works for Amazonia
  //  private static final Pattern meta1 = Pattern
  //      .compile("^(?:[a-zA-Z\\-]*(\\d+)).*?p=(\\d+).*");
  //
  //  // works for selva cie
  //  private static final Pattern meta2 = Pattern
  //    .compile("^(?:[a-zA-Z\\-]*(\\d+)).*?p=(\\d+).*");

  private int getTextID(Sentence paragraph) {
    
    final String meta = paragraph.metadata();
    Type corpusType;
    Pattern metaPattern;
    int textIdMeta2 = -1;
    String textMeta2 = "";

    if (meta.startsWith("LIT")) {
      corpusType = Type.lit;
      metaPattern = Pattern.compile("^([a-zA-Z\\-]+)(\\d+).*?p=(\\d+).*");
    } else if (meta.startsWith("CIE")) {
      corpusType = Type.cie;
      metaPattern = Pattern.compile("^.*?source=\"(.*?)\".*");
    } else { // ama
      corpusType = Type.ama;
      metaPattern = Pattern.compile("^(?:[a-zA-Z\\-]*(\\d+)).*?p=(\\d+).*");
    }

    if (corpusType.equals(Type.lit)) {
      Matcher m2 = metaPattern.matcher(meta);
      if (m2.matches()) {
        String textId = m2.group(1);
        if (!textId.equals(textMeta2)) {
          textIdMeta2++;
          textMeta2 = textId;
        }
        return textIdMeta2;
      } else {
        throw new RuntimeException("Invalid metadata: " + meta);
      }
    } else if (corpusType.equals(Type.cie)) {
      Matcher m2 = metaPattern.matcher(meta);
      if (m2.matches()) {
        String textId = m2.group(1);
        if (!textId.equals(textMeta2)) {
          textIdMeta2++;
          textMeta2 = textId;
        }
        return textIdMeta2;
      } else {
        throw new RuntimeException("Invalid metadata: " + meta);
      }
    } else if (corpusType.equals(Type.ama)) {
      Matcher m2 = metaPattern.matcher(meta);
      if (m2.matches()) {
        return Integer.parseInt(m2.group(1));
        // currentPara = Integer.parseInt(m.group(2));
      } else {
        throw new RuntimeException("Invalid metadata: " + meta);
      }
    }

    return 0;
  }

}
