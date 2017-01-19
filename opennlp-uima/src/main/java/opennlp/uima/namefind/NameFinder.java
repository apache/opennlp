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

package opennlp.uima.namefind;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.Span;
import opennlp.tools.util.eval.Mean;
import opennlp.uima.util.AnnotatorUtil;
import opennlp.uima.util.UimaUtil;

/**
 * OpenNLP Name annotator.
 * <p>
 * Mandatory parameters
 * <table border=1>
 * <caption></caption>
 * <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 * <tr><td>String</td> <td>opennlp.uima.ModelName</td> <td>The name of the model file</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.SentenceType</td> <td>The full name of the sentence type</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.TokenType</td> <td>The full name of the token type</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.NameType</td> <td>The full name of the name type</td></tr>
 * </table>
 * <p>
 * Optional parameters
 * <table border=1>
 * <caption></caption>
 * <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 * <tr><td>String</td> <td>opennlp.uima.ProbabilityFeature</td> <td>The name of the double
 * probability feature (not set by default)</td></tr>
 * <tr><td>Integer</td> <td>opennlp.uima.BeamSize</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.DocumentConfidenceType</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.DocumentConfidenceType</td></tr>
 * </table>
 */
public final class NameFinder extends AbstractNameFinder {

  public static final String NAME_TYPE_PARAMETER = "opennlp.uima.NameType";
  public static final String NAME_TYPE_MAP_PARAMETER = "opennlp.uima.NameTypeMap";

  public static final String TOKEN_PATTERN_OPTIMIZATION =
      "opennlp.uima.TokenPatternOptimization";

  // Token feature
  public static final String TOKEN_FEATURE_PARAMETER =
      "opennlp.uima.namefinder.TokenFeature";

  public static final String TOKEN_FEATURE_PREV_WINDOW_SIZE_PARAMETER =
      TOKEN_FEATURE_PARAMETER + ".previousWindowSize";

  public static final String TOKEN_FEATURE_NEXT_WINDOW_SIZE_PARAMETER =
      TOKEN_FEATURE_PARAMETER + ".nextWindowSize";

  // Token class feature
  public static final String TOKEN_CLASS_FEATURE_PARAMETER =
      "opennlp.uima.namefinder.TokenClassFeature";

  public static final String TOKEN_CLASS_FEATURE_PREV_WINDOW_SIZE_PARAMETER =
      TOKEN_CLASS_FEATURE_PARAMETER + ".previousWindowSize";

  public static final String TOKEN_CLASS_FEATURE_NEXT_WINDOW_SIZE_PARAMETER =
      TOKEN_CLASS_FEATURE_PARAMETER + ".nextWindowSize";

  private NameFinderME mNameFinder;

  private Feature probabilityFeature;

  private Type documentConfidenceType;
  private Feature documentConfidenceNameTypeFeature;
  private Feature documentConfidenceFeature;

  private Mean documentConfidence = new Mean();

  /**
   * Initializes a new instance.
   * <p>
   * Note: Use {@link #initialize(UimaContext) } to initialize
   * this instance. Not use the constructor.
   */
  public NameFinder() {
    super("OpenNLP Maxent Name annotator");
  }

  /**
   * Initializes the current instance with the given context.
   * <p>
   * Note: Do all initialization in this method, do not use the constructor.
   */
  public void initialize()
      throws ResourceInitializationException {

    super.initialize();

    TokenNameFinderModel model;

    try {
      TokenNameFinderModelResource modelResource =
          (TokenNameFinderModelResource) context.getResourceObject(UimaUtil.MODEL_PARAMETER);

      model = modelResource.getModel();
    } catch (ResourceAccessException e) {
      throw new ResourceInitializationException(e);
    }

    mNameFinder = new NameFinderME(model);
  }

  /**
   * Initializes the type system.
   */
  public void typeSystemInit(TypeSystem typeSystem)
      throws AnalysisEngineProcessException {

    super.typeSystemInit(typeSystem);

    probabilityFeature = AnnotatorUtil.getOptionalFeatureParameter(context, mNameType,
        UimaUtil.PROBABILITY_FEATURE_PARAMETER, CAS.TYPE_NAME_DOUBLE);

    documentConfidenceType = AnnotatorUtil.getOptionalTypeParameter(context, typeSystem,
        "opennlp.uima.DocumentConfidenceType");
    if (documentConfidenceType != null) {
      documentConfidenceNameTypeFeature = AnnotatorUtil.getRequiredFeature(
          documentConfidenceType, "nameType");
      documentConfidenceFeature = AnnotatorUtil.getRequiredFeature(
          documentConfidenceType, "confidence");
    }
  }

  protected Span[] find(CAS cas, String[] tokens) {

    Span names[] = mNameFinder.find(tokens);

    double probs[] = mNameFinder.probs();

    for (double prob : probs) {
      documentConfidence.add(prob);
    }

    return names;
  }

  protected void postProcessAnnotations(Span detectedNames[],
                                        AnnotationFS[] nameAnnotations) {

    if (probabilityFeature != null) {
      double[] probs = mNameFinder.probs(detectedNames);

      for (int i = 0; i < nameAnnotations.length; i++) {
        nameAnnotations[i].setDoubleValue(probabilityFeature, probs[i]);
      }
    }
  }

  protected void documentDone(CAS cas) {

    // TODO: Create confidence FS
    // contains String name type
    // contains Double prob
    if (documentConfidenceType != null) {
      FeatureStructure confidenceFS = cas.createFS(documentConfidenceType);
      confidenceFS.setDoubleValue(documentConfidenceFeature,
          documentConfidence.mean());
      confidenceFS.setStringValue(documentConfidenceNameTypeFeature,
          mNameType.getName());
      cas.addFsToIndexes(confidenceFS);
    }

    // Clears the adaptive data which was created for the current document
    mNameFinder.clearAdaptiveData();

    documentConfidence = new Mean();
  }

  /**
   * Releases allocated resources.
   */
  public void destroy() {
    mNameFinder = null;
  }
}