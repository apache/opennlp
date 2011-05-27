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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opennlp.tools.formats.ad.ADSentenceStream.Sentence;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.Leaf;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.Node;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.TreeElement;
import opennlp.tools.namefind.NameSample;
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
  
  /** 
   * Map to the Arvores Deitadas types to our types. It is read-only.
   */
  private static final Map<String, String> HAREM;

  static {
    Map<String, String> harem = new HashMap<String, String>();

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
  
  /**
   * Creates a new {@link NameSample} stream from a line stream, i.e.
   * {@link ObjectStream}< {@link String}>, that could be a
   * {@link PlainTextByLineStream} object.
   * 
   * @param lineStream
   *          a stream of lines as {@link String}
   */
  public ADNameSampleStream(ObjectStream<String> lineStream) {
    this.adSentenceStream = new ADSentenceStream(lineStream);
  }

  /**
   * Creates a new {@link NameSample} stream from a {@link InputStream}
   * 
   * @param in
   *          the Corpus {@link InputStream}
   * @param charsetName
   *          the charset of the Arvores Deitadas Corpus
   */
  public ADNameSampleStream(InputStream in, String charsetName) {

    try {
      this.adSentenceStream = new ADSentenceStream(new PlainTextByLineStream(
          in, charsetName));
    } catch (UnsupportedEncodingException e) {
      // UTF-8 is available on all JVMs, will never happen
      throw new IllegalStateException(e);
    }
  }

  public NameSample read() throws IOException {

    Sentence paragraph;
    while ((paragraph = this.adSentenceStream.read()) != null) {
      Node root = paragraph.getRoot();
      List<String> sentence = new ArrayList<String>();
      List<Span> names = new ArrayList<Span>();
      process(root, sentence, names);

      return new NameSample(sentence.toArray(new String[sentence.size()]),
          names.toArray(new Span[names.size()]), true);
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

    if (leaf != null && leftContractionPart == null) {

      String namedEntityTag = null;
      int startOfNamedEntity = -1;

      String leafTag = leaf.getMorphologicalTag();
      boolean expandLastNER = false; // used when we find a <NER2> tag

      if (leafTag != null) {
        if (leafTag.contains("<sam->")) {
          String[] lexemes = leaf.getLexeme().split("_");
          if(lexemes.length > 1) {
            for (int i = 0; i < lexemes.length - 1; i++) {
              sentence.add(lexemes[i]);
            }
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

      sentence.addAll(Arrays.asList(leaf.getLexeme().split("_")));

      if (namedEntityTag != null) {
        names
            .add(new Span(startOfNamedEntity, sentence.size(), namedEntityTag));
      }

      if (expandLastNER) {
        // if the current leaf has the tag <NER2>, it can be the continuation of
        // a NER.
        // we check if it is true, and expand the lest NER
        int lastIndex = names.size() - 1;
        Span last = null;
        boolean error = false;
        if (names.size() > 0) {
          last = names.get(lastIndex);
          if (last.getEnd() == sentence.size() - 1) {
            names.set(lastIndex, new Span(last.getStart(), sentence.size(),
                last.getType()));
          } else {
            error = true;
          }
        } else {
          error = true;
        }
        if (error) {
          // Maybe it is not the same NER, skip it.
          // System.err.println("Missing NER start for sentence [" + sentence
          // + "] node [" + leaf + "]");
        }
      }

    } else {
      // will handle the contraction
      String tag = leaf.getMorphologicalTag();
      String right = leaf.getLexeme();
      if (tag != null && tag.contains("<-sam>")) {
        right = leaf.getLexeme();
        String c = PortugueseContractionUtility.toContraction(leftContractionPart, right);

        if (c != null) {
          sentence.add(c);
        } else {
          System.err.println("missing " + leftContractionPart + " + " + right);
          sentence.add(leftContractionPart);
          sentence.add(right);
        }

      } else {
        System.err.println("unmatch" + leftContractionPart + " + " + right);
      }
      leftContractionPart = null;
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

}
