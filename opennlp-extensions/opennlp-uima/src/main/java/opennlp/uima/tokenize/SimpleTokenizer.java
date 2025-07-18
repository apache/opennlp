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

package opennlp.uima.tokenize;

import org.apache.uima.UimaContext;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.text.AnnotationFS;

import opennlp.tools.util.Span;

/**
 * An OpenNLP-based Simple Tokenizer annotator.
 * <p>
 * Mandatory parameters:
 * <table border=1>
 * <caption></caption>
 * <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 * <tr><td>String</td>
 *  <td>{@code opennlp.uima.SentenceType}</td>
 *  <td>The full name of the sentence type</td>
 * </tr>
 * <tr><td>String</td>
 *  <td>{@code opennlp.uima.TokenType}</td>
 *  <td>The full name of the token type</td>
 * </tr>
 * </table>
 */
public final class SimpleTokenizer extends AbstractTokenizer {

  /**
   * The OpenNLP simple tokenizer.
   */
  private final opennlp.tools.tokenize.SimpleTokenizer tokenizer =
      opennlp.tools.tokenize.SimpleTokenizer.INSTANCE;

  /**
   * Initializes the current instance.
   * <p>
   * Note: Use {@link #initialize(UimaContext) } to initialize
   * this instance. Not use the constructor.
   */
  public SimpleTokenizer() {
    super("OpenNLP Simple Tokenizer");
    // must not be implemented !
  }

  @Override
  protected Span[] tokenize(CAS cas, AnnotationFS sentence) {
    return tokenizer.tokenizePos(sentence.getCoveredText());
  }

  @Override
  protected void postProcessAnnotations(Span[] detectedNames, AnnotationFS[] nameAnnotations) {
    // nothing to do
  }
}
