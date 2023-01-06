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

package opennlp.tools.formats.conllu;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class ConlluSentence {

  private final List<ConlluWordLine> wordLines;

  private final String sentenceIdComment;
  private final String textComment;
  private boolean newDocument;
  private String documentId;
  private boolean newParagraph;
  private String paragraphId;
  private Map<Locale, String> textLang;
  private String translit;

  ConlluSentence(List<ConlluWordLine> wordLines, String sentenceIdComment, String textComment) {
    this.wordLines = wordLines;
    this.sentenceIdComment = sentenceIdComment;
    this.textComment = textComment;
  }

  public ConlluSentence(List<ConlluWordLine> wordLines, String sentenceIdComment, String textComment,
                        boolean newDocument, String documentId, boolean newParagraph, String paragraphId,
                        Map<Locale, String> textLang, String translit) {
    this.wordLines = wordLines;
    this.sentenceIdComment = sentenceIdComment;
    this.textComment = textComment;
    this.newDocument = newDocument;
    this.documentId = documentId;
    this.newParagraph = newParagraph;
    this.paragraphId = paragraphId;
    this.textLang = textLang;
    this.translit = translit;
  }

  public List<ConlluWordLine> getWordLines() {
    return wordLines;
  }

  public String getSentenceIdComment() {
    return sentenceIdComment;
  }

  public String getTextComment() {
    return textComment;
  }

  public boolean isNewDocument() {
    return newDocument;
  }

  public Optional<String> getDocumentId() {
    return Optional.ofNullable(documentId);
  }

  public boolean isNewParagraph() {
    return newParagraph;
  }

  public Optional<String> getParagraphId() {
    return Optional.ofNullable(paragraphId);
  }

  public Optional<Map<Locale, String>> getTextLang() {
    return Optional.ofNullable(textLang);
  }

  public Optional<String> getTranslit() {
    return Optional.ofNullable(translit);
  }
}
