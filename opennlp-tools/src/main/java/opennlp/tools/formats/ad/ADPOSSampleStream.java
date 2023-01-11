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
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import opennlp.tools.commons.Internal;
import opennlp.tools.formats.ad.ADSentenceStream.Sentence;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.Leaf;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.Node;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.TreeElement;
import opennlp.tools.postag.POSSample;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * <b>Note:</b>
 * Do not use this class, internal use only!
 */
@Internal
public class ADPOSSampleStream implements ObjectStream<POSSample> {

  private static final Pattern WHITESPACES_PATTERN = Pattern.compile("\\s+");

  private final ObjectStream<ADSentenceStream.Sentence> adSentenceStream;
  private final boolean expandME;
  private final boolean isIncludeFeatures;

  /**
   * Creates a new {@link ADPOSSampleStream} stream from a {@link ObjectStream<String>},
   * that could be a {@link PlainTextByLineStream} object.
   *
   * @param lineStream A {@link ObjectStream<String>} stream as input.
   * @param expandME If {@code true} will expand the multiword expressions, each word of the
   *          expression will have the POS Tag that was attributed to the
   *          expression plus the prefix {@code B-} or {@code I-} (CONLL convention).
   * @param includeFeatures If {@code true} will combine the POS Tag with the feature tags.
   */
  public ADPOSSampleStream(ObjectStream<String> lineStream, boolean expandME,
      boolean includeFeatures) {
    this.adSentenceStream = new ADSentenceStream(lineStream);
    this.expandME = expandME;
    this.isIncludeFeatures = includeFeatures;
  }

  /**
   * Creates a new {@link POSSample} stream from an {@link InputStreamFactory}
   *
   * @param in The {@link InputStreamFactory} for the corpus.
   * @param charsetName  The {@link java.nio.charset.Charset charset} to use
   *                     for reading of the corpus.
   * @param expandME If {@code true} will expand the multiword expressions, each word of the
   *          expression will have the POS Tag that was attributed to the
   *          expression plus the prefix {@code B-} or {@code I-} (CONLL convention).
   * @param includeFeatures If {@code true} will combine the POS Tag with the feature tags.
   */
  public ADPOSSampleStream(InputStreamFactory in, String charsetName,
      boolean expandME, boolean includeFeatures) throws IOException {

    this(new PlainTextByLineStream(in, charsetName), expandME, includeFeatures);
  }

  @Override
  public POSSample read() throws IOException {
    Sentence paragraph;
    if ((paragraph = this.adSentenceStream.read()) != null) {
      Node root = paragraph.getRoot();
      List<String> sentence = new ArrayList<>();
      List<String> tags = new ArrayList<>();
      process(root, sentence, tags);

      return new POSSample(sentence, tags);
    }
    return null;
  }

  private void process(Node node, List<String> sentence, List<String> tags) {
    if (node != null) {
      for (TreeElement element : node.getElements()) {
        if (element.isLeaf()) {
          processLeaf((Leaf) element, sentence, tags);
        } else {
          process((Node) element, sentence, tags);
        }
      }
    }
  }

  private void processLeaf(Leaf leaf, List<String> sentence, List<String> tags) {
    if (leaf != null) {
      String lexeme = leaf.getLexeme();
      String tag = leaf.getFunctionalTag();

      if (tag == null) {
        tag = leaf.getLexeme();
      }

      if (isIncludeFeatures && leaf.getMorphologicalTag() != null) {
        tag += " " + leaf.getMorphologicalTag();
      }
      tag = WHITESPACES_PATTERN.matcher(tag).replaceAll("=");

      if (tag == null)
        tag = lexeme;

      if (expandME && lexeme.contains("_")) {
        StringTokenizer tokenizer = new StringTokenizer(lexeme, "_");

        if (tokenizer.countTokens() > 0) {
          List<String> toks = new ArrayList<>(tokenizer.countTokens());
          List<String> tagsWithCont = new ArrayList<>(
              tokenizer.countTokens());
          toks.add(tokenizer.nextToken());
          tagsWithCont.add("B-" + tag);
          while (tokenizer.hasMoreTokens()) {
            toks.add(tokenizer.nextToken());
            tagsWithCont.add("I-" + tag);
          }

          sentence.addAll(toks);
          tags.addAll(tagsWithCont);
        } else {
          sentence.add(lexeme);
          tags.add(tag);
        }

      } else {
        sentence.add(lexeme);
        tags.add(tag);
      }
    }

  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    adSentenceStream.reset();
  }

  @Override
  public void close() throws IOException {
    adSentenceStream.close();
  }
}
