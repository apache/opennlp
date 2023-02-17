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
   * Pattern of a NER tag in Arvores Deitadas
   */
  private static final Pattern TAG_PATTERN = Pattern.compile("<(NER:)?(.*?)>");
  private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
  private static final Pattern UNDERLINE_PATTERN = Pattern.compile("[_]+");
  private static final Pattern HYPHEN_PATTERN =
      Pattern.compile("((\\p{L}+)-$)|(^-(\\p{L}+)(.*))|((\\p{L}+)-(\\p{L}+)(.*))");
  private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[\\p{L}\\p{Nd}]+$");

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
  @Deprecated
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

      Node root = paragraph.getRoot();
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
        String[] parts = WHITESPACE_PATTERN.split(c);
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
        String[] lexemes = UNDERLINE_PATTERN.split(leaf.getLexeme());
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
    String[] parts = UNDERLINE_PATTERN.split(lexemeStr);
    for (String tok : parts) {
      if (tok.length() > 1 && !ALPHANUMERIC_PATTERN.matcher(tok).matches()) {
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
      Matcher matcher = HYPHEN_PATTERN.matcher(tok);

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
          && !ALPHANUMERIC_PATTERN.matcher(tok).matches()) {
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
   * Parses a NER tag in Arvores Deitadas format.
   *
   * @param tags The NER tag in Arvores Deitadas format.
   * @return The NER tag, or {@code null} if not a NER tag in Arvores Deitadas format.
   */
  private static String getNER(String tags) {
    if (tags.contains("<NER2>")) {
      return null;
    }
    String[] tag = tags.split("\\s+");
    for (String t : tag) {
      Matcher matcher = TAG_PATTERN.matcher(t);
      if (matcher.matches()) {
        String ner = matcher.group(2);
        if (HAREM.containsKey(ner)) {
          return HAREM.get(ner);
        }
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
    
    final String meta = paragraph.getMetadata();
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
