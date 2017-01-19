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

package opennlp.uima.postag;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.uima.util.AnnotationComboIterator;
import opennlp.uima.util.AnnotationIteratorPair;
import opennlp.uima.util.AnnotatorUtil;
import opennlp.uima.util.UimaUtil;

/**
 * OpenNLP Part Of Speech annotator.
 * <p>
 * Mandatory parameters
 * <table border=1>
 * <caption></caption>
 * <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 * <tr><td>String</td> <td>opennlp.uima.ModelName</td> <td>The name of the model file</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.SentenceType</td> <td>The full name of the sentence type</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.TokenType</td> <td>The full name of the token type</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.POSFeature</td> <td>The name of the token pos feature,
 * the feature must be of type String</td></tr>
 * </table>
 * <p>
 * Optional parameters
 * <table border=1>
 * <caption></caption>
 * <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 * <tr><td>String</td> <td>opennlp.uima.ProbabilityFeature</td>
 * <td>The name of the double probability feature (not set by default)</td></tr>
 * <tr><td>Integer</td> <td>opennlp.uima.BeamSize</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.DictionaryName</td> <td>The name of the dictionary file</td></tr>
 * </table>
 */
public final class POSTagger extends CasAnnotator_ImplBase {

  private POSTaggerME posTagger;

  private Type sentenceType;

  private Type tokenType;

  private Feature posFeature;

  private Feature probabilityFeature;

  private UimaContext context;

  private Logger logger;

  /**
   * Initializes a new instance.
   * <p>
   * Note: Use {@link #initialize(UimaContext) } to initialize this instance. Not use the
   * constructor.
   */
  public POSTagger() {
    // must not be implemented !
  }

  /**
   * Initializes the current instance with the given context.
   * <p>
   * Note: Do all initialization in this method, do not use the constructor.
   */
  @Override
  public void initialize(UimaContext context)
      throws ResourceInitializationException {

    super.initialize(context);

    this.context = context;

    this.logger = context.getLogger();

    if (this.logger.isLoggable(Level.INFO)) {
      this.logger.log(Level.INFO, "Initializing the OpenNLP "
          + "Part of Speech annotator.");
    }

    POSModel model;

    try {
      POSModelResource modelResource = (POSModelResource) context
          .getResourceObject(UimaUtil.MODEL_PARAMETER);

      model = modelResource.getModel();
    } catch (ResourceAccessException e) {
      throw new ResourceInitializationException(e);
    }

    Integer beamSize = AnnotatorUtil.getOptionalIntegerParameter(context,
        UimaUtil.BEAM_SIZE_PARAMETER);

    if (beamSize == null) {
      beamSize = POSTaggerME.DEFAULT_BEAM_SIZE;
    }

    this.posTagger = new POSTaggerME(model);
  }

  /**
   * Initializes the type system.
   */
  @Override
  public void typeSystemInit(TypeSystem typeSystem) throws AnalysisEngineProcessException {

    // sentence type
    this.sentenceType = AnnotatorUtil.getRequiredTypeParameter(this.context, typeSystem,
        UimaUtil.SENTENCE_TYPE_PARAMETER);

    // token type
    this.tokenType = AnnotatorUtil.getRequiredTypeParameter(this.context, typeSystem,
        UimaUtil.TOKEN_TYPE_PARAMETER);

    // pos feature
    this.posFeature = AnnotatorUtil.getRequiredFeatureParameter(this.context, this.tokenType,
        UimaUtil.POS_FEATURE_PARAMETER, CAS.TYPE_NAME_STRING);

    this.probabilityFeature = AnnotatorUtil.getOptionalFeatureParameter(this.context,
        this.tokenType, UimaUtil.PROBABILITY_FEATURE_PARAMETER, CAS.TYPE_NAME_DOUBLE);
  }

  /**
   * Performs pos-tagging on the given tcas object.
   */
  @Override
  public void process(CAS tcas) {

    final AnnotationComboIterator comboIterator = new AnnotationComboIterator(tcas,
        this.sentenceType, this.tokenType);

    for (AnnotationIteratorPair annotationIteratorPair : comboIterator) {

      final List<AnnotationFS> sentenceTokenAnnotationList = new LinkedList<>();

      final List<String> sentenceTokenList = new LinkedList<>();

      for (AnnotationFS tokenAnnotation : annotationIteratorPair.getSubIterator()) {

        sentenceTokenAnnotationList.add(tokenAnnotation);

        sentenceTokenList.add(tokenAnnotation.getCoveredText());
      }

      final List<String> posTags = Arrays.asList(this.posTagger.tag(
          sentenceTokenList.toArray(new String[sentenceTokenList.size()])));

      double posProbabilities[] = null;

      if (this.probabilityFeature != null) {
        posProbabilities = this.posTagger.probs();
      }

      final Iterator<String> posTagIterator = posTags.iterator();
      final Iterator<AnnotationFS> sentenceTokenIterator = sentenceTokenAnnotationList.iterator();

      int index = 0;
      while (posTagIterator.hasNext() && sentenceTokenIterator.hasNext()) {
        final String posTag = posTagIterator.next();
        final AnnotationFS tokenAnnotation = sentenceTokenIterator.next();

        tokenAnnotation.setStringValue(this.posFeature, posTag);

        if (posProbabilities != null) {
          tokenAnnotation.setDoubleValue(this.probabilityFeature, posProbabilities[index]);
        }

        index++;
      }

      // log tokens with pos
      if (this.logger.isLoggable(Level.FINER)) {

        final StringBuilder sentenceWithPos = new StringBuilder();

        sentenceWithPos.append("\"");

        for (final AnnotationFS token : sentenceTokenAnnotationList) {
          sentenceWithPos.append(token.getCoveredText());
          sentenceWithPos.append('\\');
          sentenceWithPos.append(token.getStringValue(this.posFeature));
          sentenceWithPos.append(' ');
        }

        // delete last whitespace
        if (sentenceWithPos.length() > 1) // not 0 because it contains already the " char
        {
          sentenceWithPos.setLength(sentenceWithPos.length() - 1);
        }

        sentenceWithPos.append("\"");

        this.logger.log(Level.FINER, sentenceWithPos.toString());
      }
    }
  }

  /**
   * Releases allocated resources.
   */
  @Override
  public void destroy() {
    this.posTagger = null;
  }
}
