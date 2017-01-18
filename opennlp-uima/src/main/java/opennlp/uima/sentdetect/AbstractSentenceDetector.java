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

package opennlp.uima.sentdetect;

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

public abstract class AbstractSentenceDetector extends CasAnnotator_ImplBase {

  protected UimaContext context;

  /**
   * The type of the sentences to be created.
   */
  protected Logger logger;

  protected Type containerType;

  protected Type sentenceType;

  private Boolean isRemoveExistingAnnotations;

  @Override
  public void initialize(UimaContext context)
      throws ResourceInitializationException {
    super.initialize(context);

    this.context = context;

    logger = context.getLogger();

    if (logger.isLoggable(Level.INFO)) {
      logger.log(Level.INFO, "Initializing the OpenNLP Sentence annotator.");
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

    containerType = AnnotatorUtil.getOptionalTypeParameter(context, typeSystem,
        "opennlp.uima.ContainerType");

    if (containerType == null) {
      containerType = typeSystem.getType(CAS.TYPE_NAME_DOCUMENT_ANNOTATION);
    }

    sentenceType = AnnotatorUtil.getRequiredTypeParameter(context, typeSystem,
        UimaUtil.SENTENCE_TYPE_PARAMETER);
  }

  protected abstract Span[] detectSentences(String text);

  protected void postProcessAnnotations(AnnotationFS sentences[]) {
  }

  @Override
  public void process(CAS cas) throws AnalysisEngineProcessException {

    FSIndex<AnnotationFS> containerAnnotations = cas
        .getAnnotationIndex(containerType);

    for (AnnotationFS containerAnnotation : containerAnnotations) {

      String text = containerAnnotation.getCoveredText();

      if (isRemoveExistingAnnotations) {
        UimaUtil.removeAnnotations(cas, containerAnnotation, sentenceType);
      }

      Span[] sentPositions = detectSentences(text);

      AnnotationFS sentences[] = new AnnotationFS[sentPositions.length];

      for (int i = 0; i < sentPositions.length; i++) {

        sentences[i] = cas.createAnnotation(sentenceType,
            sentPositions[i].getStart() + containerAnnotation.getBegin(),
            sentPositions[i].getEnd() + containerAnnotation.getBegin());

        cas.getIndexRepository().addFS(sentences[i]);

        if (logger.isLoggable(Level.FINER)) {
          logger.log(Level.FINER, "\"" + sentences[i].getCoveredText() + "\"");
        }
      }

      postProcessAnnotations(sentences);
    }
  }
}
