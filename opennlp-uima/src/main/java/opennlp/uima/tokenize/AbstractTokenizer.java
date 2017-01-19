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
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import opennlp.tools.util.Span;
import opennlp.uima.util.AnnotatorUtil;
import opennlp.uima.util.UimaUtil;

public abstract class AbstractTokenizer extends CasAnnotator_ImplBase {

  protected final String name;

  protected UimaContext context;

  protected Logger logger;

  /**
   * Type of the sentence containing the tokens.
   */
  protected Type sentenceType;

  /**
   * Type of the tokens to be created.
   */
  protected Type tokenType;

  private Boolean isRemoveExistingAnnotations;

  protected AbstractTokenizer(String name) {
    this.name = name;
  }

  @Override
  public void initialize(UimaContext context)
      throws ResourceInitializationException {

    super.initialize(context);

    this.context = context;

    logger = context.getLogger();

    if (logger.isLoggable(Level.INFO)) {
      logger.log(Level.INFO, "Initializing the " + name + " annotator.");
    }

    isRemoveExistingAnnotations = AnnotatorUtil.getOptionalBooleanParameter(
        context, UimaUtil.IS_REMOVE_EXISTINGS_ANNOTAIONS);

    if (isRemoveExistingAnnotations == null) {
      isRemoveExistingAnnotations = false;
    }
  }

  @Override
  public void typeSystemInit(TypeSystem typeSystem)
      throws AnalysisEngineProcessException {
    super.typeSystemInit(typeSystem);

    sentenceType = AnnotatorUtil.getRequiredTypeParameter(context, typeSystem,
        UimaUtil.SENTENCE_TYPE_PARAMETER);

    tokenType = AnnotatorUtil.getRequiredTypeParameter(context, typeSystem,
        UimaUtil.TOKEN_TYPE_PARAMETER);
  }

  protected void postProcessAnnotations(Span tokens[],
                                        AnnotationFS tokenAnnotations[]) {
  }

  protected abstract Span[] tokenize(CAS cas, AnnotationFS sentence);

  @Override
  public void process(CAS cas) throws AnalysisEngineProcessException {
    FSIndex<AnnotationFS> sentences = cas.getAnnotationIndex(sentenceType);

    for (AnnotationFS sentence : sentences) {

      if (isRemoveExistingAnnotations) {
        UimaUtil.removeAnnotations(cas, sentence, tokenType);
      }

      Span tokenSpans[] = tokenize(cas, sentence);

      int sentenceOffset = sentence.getBegin();

      StringBuilder tokeninzedSentenceLog = new StringBuilder();

      AnnotationFS tokenAnnotations[] = new AnnotationFS[tokenSpans.length];

      for (int i = 0; i < tokenSpans.length; i++) {
        tokenAnnotations[i] = cas
            .createAnnotation(tokenType,
                sentenceOffset + tokenSpans[i].getStart(), sentenceOffset
                    + tokenSpans[i].getEnd());

        cas.getIndexRepository().addFS(tokenAnnotations[i]);

        if (logger.isLoggable(Level.FINER)) {
          tokeninzedSentenceLog.append(tokenAnnotations[i].getCoveredText());
          tokeninzedSentenceLog.append(' ');
        }
      }

      if (logger.isLoggable(Level.FINER)) {
        // remove last space
        tokeninzedSentenceLog.delete(tokeninzedSentenceLog.length() - 2,
            tokeninzedSentenceLog.length() - 1);

        logger.log(Level.FINER, "\"" + tokeninzedSentenceLog.toString() + "\"");
      }

      postProcessAnnotations(tokenSpans, tokenAnnotations);
    }
  }
}
