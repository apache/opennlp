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
import java.util.List;

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.commons.Internal;
import opennlp.tools.formats.ad.ADSentenceStream.Sentence;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.Leaf;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.Node;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.TreeElement;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.StringUtil;

/**
 * Parser for Floresta Sita(c)tica Arvores Deitadas corpus, output to for the
 * Portuguese Chunker training.
 * <p>
 * The heuristic to extract chunks where based o paper 'A Machine Learning
 * Approach to Portuguese Clause Identification', (Eraldo Fernandes, Cicero
 * Santos and Ruy Milidiú).<br>
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
 * <b>Note:</b> Do not use this class, internal use only!
 */
@Internal
public class ADChunkSampleStream implements ObjectStream<ChunkSample> {

  protected final ObjectStream<ADSentenceStream.Sentence> adSentenceStream;

  private int start = -1;
  private int end = -1;

  private int index = 0;

  public static final String OTHER = "O";

  /**
   * Instantiates a {@link ADChunkSampleStream} stream from {@link ObjectStream<String>},
   * that could be a {@link PlainTextByLineStream} object.
   *
   * @param lineStream An {@link ObjectStream<String>} as input.
   */
  public ADChunkSampleStream(ObjectStream<String> lineStream) {
    this.adSentenceStream = new ADSentenceStream(lineStream);
  }

  /**
   * Instantiates a {@link ADChunkSampleStream} stream from an {@link InputStreamFactory}.
   *
   * @param in The {@link InputStreamFactory} for the corpus.
   * @param charsetName  The {@link java.nio.charset.Charset charset} to use
   *                     for reading of the corpus.
   */
  public ADChunkSampleStream(InputStreamFactory in, String charsetName) throws IOException {
    this(new PlainTextByLineStream(in, charsetName));
  }

  @Override
  public ChunkSample read() throws IOException {

    Sentence paragraph;
    while ((paragraph = this.adSentenceStream.read()) != null) {

      if (end > -1 && index >= end) {
        // leave
        return null;
      }

      if (start > -1 && index < start) {
        index++;
        // skip this one
      } else {
        Node root = paragraph.getRoot();
        List<String> sentence = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        List<String> target = new ArrayList<>();

        processRoot(root, sentence, tags, target);

        if (sentence.size() > 0) {
          index++;
          return new ChunkSample(sentence, tags, target);
        }

      }

    }
    return null;
  }

  protected void processRoot(Node root, List<String> sentence, List<String> tags,
      List<String> target) {
    if (root != null) {
      TreeElement[] elements = root.getElements();
      for (TreeElement element : elements) {
        if (element.isLeaf()) {
          processLeaf((Leaf) element, false, OTHER, sentence, tags, target);
        } else {
          processNode((Node) element, sentence, tags, target, null);
        }
      }
    }
  }

  private void processNode(Node node, List<String> sentence, List<String> tags,
      List<String> target, String inheritedTag) {
    String phraseTag = getChunkTag(node);

    boolean inherited = false;
    if (phraseTag.equals(OTHER) && inheritedTag != null) {
      phraseTag = inheritedTag;
      inherited = true;
    }

    TreeElement[] elements = node.getElements();
    for (int i = 0; i < elements.length; i++) {
      if (elements[i].isLeaf()) {
        boolean isIntermediate = false;
        String tag = phraseTag;
        Leaf leaf = (Leaf) elements[i];

        String localChunk = getChunkTag(leaf);
        if (localChunk != null && !tag.equals(localChunk)) {
          tag = localChunk;
        }

        if (isIntermediate(tags, target, tag) && (inherited || i > 0)) {
          isIntermediate = true;
        }
        if (!isIncludePunctuations() && leaf.getFunctionalTag() == null &&
            (
                !( i + 1 < elements.length && elements[i + 1].isLeaf() ) ||
                !( i > 0 && elements[i - 1].isLeaf() )
                )
            ) {
          isIntermediate = false;
          tag = OTHER;
        }
        processLeaf(leaf, isIntermediate, tag, sentence,
            tags, target);
      } else {
        int before = target.size();
        processNode((Node) elements[i], sentence, tags, target, phraseTag);

        // if the child node was of a different type we should break the chunk sequence
        for (int j = target.size() - 1; j >= before; j--) {
          if (!target.get(j).endsWith("-" + phraseTag)) {
            phraseTag = OTHER;
            break;
          }
        }
      }
    }
  }


  protected void processLeaf(Leaf leaf, boolean isIntermediate, String phraseTag,
      List<String> sentence, List<String> tags, List<String> target) {
    String chunkTag;

    if (leaf.getFunctionalTag() != null
        && phraseTag.equals(OTHER)) {
      phraseTag = getPhraseTagFromPosTag(leaf.getFunctionalTag());
    }

    if (!phraseTag.equals(OTHER)) {
      if (isIntermediate) {
        chunkTag = "I-" + phraseTag;
      } else {
        chunkTag = "B-" + phraseTag;
      }
    } else {
      chunkTag = phraseTag;
    }

    sentence.add(leaf.getLexeme());
    if (leaf.getSyntacticTag() == null) {
      tags.add(leaf.getLexeme());
    } else {
      tags.add(ADChunkSampleStream.convertFuncTag(leaf.getFunctionalTag(), false));
    }
    target.add(chunkTag);
  }

  protected String getPhraseTagFromPosTag(String functionalTag) {
    if (functionalTag.equals("v-fin")) {
      return "VP";
    } else if (functionalTag.equals("n")) {
      return "NP";
    }
    return OTHER;
  }

  public static String convertFuncTag(String t, boolean useCGTags) {
    if (useCGTags) {
      if ("art".equals(t) || "pron-det".equals(t) || "pron-indef".equals(t)) {
        t = "det";
      }
    }
    return t;
  }

  protected String getChunkTag(Leaf leaf) {
    String tag = leaf.getSyntacticTag();
    if ("P".equals(tag)) {
      return "VP";
    }
    return null;
  }

  protected String getChunkTag(Node node) {
    String tag = node.getSyntacticTag();

    String phraseTag = tag.substring(tag.lastIndexOf(":") + 1);

    while (phraseTag.endsWith("-")) {
      phraseTag = phraseTag.substring(0, phraseTag.length() - 1);
    }

    // maybe we should use only np, vp and pp, but will keep ap and advp.
    if (phraseTag.equals("np") || phraseTag.equals("vp")
        || phraseTag.equals("pp") || phraseTag.equals("ap")
        || phraseTag.equals("advp") || phraseTag.equals("adjp")) {
      phraseTag = StringUtil.toUpperCase(phraseTag);
    } else {
      phraseTag = OTHER;
    }
    return phraseTag;
  }

  public void setStart(int aStart) {
    this.start = aStart;
  }

  public void setEnd(int aEnd) {
    this.end = aEnd;
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    adSentenceStream.reset();
  }

  @Override
  public void close() throws IOException {
    adSentenceStream.close();
  }

  protected boolean isIncludePunctuations() {
    return false;
  }

  protected boolean isIntermediate(List<String> tags, List<String> target,
      String phraseTag) {
    return target.size() > 0
        && target.get(target.size() - 1).endsWith("-" + phraseTag);
  }

}
