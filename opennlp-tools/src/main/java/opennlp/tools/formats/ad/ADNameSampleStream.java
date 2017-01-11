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
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * Data can be found on this web site:<br>
 * http://www.linguateca.pt/floresta/corpus.html
 * <p>
 * Information about the format:<br>
 * Susana Afonso.
 * "Árvores deitadas: Descrição do formato e das opções de análise na Floresta Sintáctica"
 * .<br>
 * 12 de Fevereiro de 2006.
 * http://www.linguateca.pt/documentos/Afonso2006ArvoresDeitadas.pdf
 * <p>
 * Detailed info about the NER tagset:
 * http://beta.visl.sdu.dk/visl/pt/info/portsymbol.html#semtags_names
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class ADNameSampleStream implements ObjectStream<NameSample> {

  /**
   * Pattern of a NER tag in Arvores Deitadas
   */
  private static final Pattern tagPattern = Pattern.compile("<(NER:)?(.*?)>");

  private static final Pattern whitespacePattern = Pattern.compile("\\s+");
  private static final Pattern underlinePattern = Pattern.compile("[_]+");
  private static final Pattern hyphenPattern =
      Pattern.compile("((\\p{L}+)-$)|(^-(\\p{L}+)(.*))|((\\p{L}+)-(\\p{L}+)(.*))");
  private static final Pattern alphanumericPattern = Pattern.compile("^[\\p{L}\\p{Nd}]+$");

  /**
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

  /**
   * To keep the last left contraction part
   */
  private String leftContractionPart = null;

  private final boolean splitHyphenatedTokens;

  /**
   * Creates a new {@link NameSample} stream from a line stream, i.e.
   * {@link ObjectStream}&lt;{@link String}&gt;, that could be a
   * {@link PlainTextByLineStream} object.
   *
   * @param lineStream
   *          a stream of lines as {@link String}
   * @param splitHyphenatedTokens
   *          if true hyphenated tokens will be separated: "carros-monstro" &gt;
   *          "carros" "-" "monstro"
   */
  public ADNameSampleStream(ObjectStream<String> lineStream, boolean splitHyphenatedTokens) {
    this.adSentenceStream = new ADSentenceStream(lineStream);
    this.splitHyphenatedTokens = splitHyphenatedTokens;
  }

  /**
   * Creates a new {@link NameSample} stream from a {@link InputStream}
   *
   * @param in
   *          the Corpus {@link InputStream}
   * @param charsetName
   *          the charset of the Arvores Deitadas Corpus
   * @param splitHyphenatedTokens
   *          if true hyphenated tokens will be separated: "carros-monstro" &gt;
   *          "carros" "-" "monstro"
   */
  @Deprecated
  public ADNameSampleStream(InputStreamFactory in, String charsetName,
      boolean splitHyphenatedTokens) throws IOException {

    try {
      this.adSentenceStream = new ADSentenceStream(new PlainTextByLineStream(
          in, charsetName));
      this.splitHyphenatedTokens = splitHyphenatedTokens;
    } catch (UnsupportedEncodingException e) {
      // UTF-8 is available on all JVMs, will never happen
      throw new IllegalStateException(e);
    }
  }

  private int textID = -1;

  public NameSample read() throws IOException {

    Sentence paragraph;
    // we should look for text here.
    while ((paragraph = this.adSentenceStream.read()) != null) {

      int currentTextID = getTextID(paragraph);
      boolean clearData = false;
      if (currentTextID != textID) {
        clearData = true;
        textID = currentTextID;
      }

      Node root = paragraph.getRoot();
      List<String> sentence = new ArrayList<>();
      List<Span> names = new ArrayList<>();
      process(root, sentence, names);

      return new NameSample(sentence.toArray(new String[sentence.size()]),
          names.toArray(new Span[names.size()]), clearData);
    }
    return null;
  }

  /**
   * Recursive method to process a node in Arvores Deitadas format.
   *
   * @param node
   *          the node to be processed
   * @param sentence
   *          the sentence tokens we got so far
   * @param names
   *          the names we got so far
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
   * Process a Leaf of Arvores Detaitadas format
   *
   * @param leaf
   *          the leaf to be processed
   * @param sentence
   *          the sentence tokens we got so far
   * @param names
   *          the names we got so far
   */
  private void processLeaf(Leaf leaf, List<String> sentence,
      List<Span> names) {

    boolean alreadyAdded = false;

    if (leftContractionPart != null) {
      // will handle the contraction
      String right = leaf.getLexeme();

      String c = PortugueseContractionUtility.toContraction(
          leftContractionPart, right);
      if (c != null) {
        String[] parts = whitespacePattern.split(c);
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
        String[] lexemes = underlinePattern.split(leaf.getLexeme());
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
    String[] parts = underlinePattern.split(lexemeStr);
    for (String tok : parts) {
      if (tok.length() > 1 && !alphanumericPattern.matcher(tok).matches()) {
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
      Matcher matcher = hyphenPattern.matcher(tok);

      String firstTok = null;
      String hyphen = "-";
      String secondTok = null;
      String rest = null;

      if (matcher.matches()) {
        if (matcher.group(1) != null) {
          firstTok = matcher.group(2);
        } else if (matcher.group(3) != null) {
          secondTok = matcher.group(4);
          rest = matcher.group(5);
        } else if (matcher.group(6) != null) {
          firstTok = matcher.group(7);
          secondTok = matcher.group(8);
          rest = matcher.group(9);
        }

        addIfNotEmpty(firstTok, out);
        addIfNotEmpty(hyphen, out);
        addIfNotEmpty(secondTok, out);
        addIfNotEmpty(rest, out);
        tokAdded = true;
      }
    }
    if (!tokAdded) {
      if (!original.equals(tok) && tok.length() > 1
          && !alphanumericPattern.matcher(tok).matches()) {
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
   * Parse a NER tag in Arvores Deitadas format.
   *
   * @param tags
   *          the NER tag in Arvores Deitadas format
   * @return the NER tag, or null if not a NER tag in Arvores Deitadas format
   */
  private static String getNER(String tags) {
    if (tags.contains("<NER2>")) {
      return null;
    }
    String[] tag = tags.split("\\s+");
    for (String t : tag) {
      Matcher matcher = tagPattern.matcher(t);
      if (matcher.matches()) {
        String ner = matcher.group(2);
        if (HAREM.containsKey(ner)) {
          return HAREM.get(ner);
        }
      }
    }
    return null;
  }

  public void reset() throws IOException, UnsupportedOperationException {
    adSentenceStream.reset();
  }

  public void close() throws IOException {
    adSentenceStream.close();
  }

  enum Type {
    ama, cie, lit
  }

  private Type corpusType = null;

  private Pattern metaPattern;

  // works for Amazonia
  //  private static final Pattern meta1 = Pattern
  //      .compile("^(?:[a-zA-Z\\-]*(\\d+)).*?p=(\\d+).*");
  //
  //  // works for selva cie
  //  private static final Pattern meta2 = Pattern
  //    .compile("^(?:[a-zA-Z\\-]*(\\d+)).*?p=(\\d+).*");

  private int textIdMeta2 = -1;
  private String textMeta2 = "";

  private int getTextID(Sentence paragraph) {

    String meta = paragraph.getMetadata();

    if (corpusType == null) {
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
