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
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;

import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;
import opennlp.uima.util.AnnotatorUtil;
import opennlp.uima.util.UimaUtil;

/**
 * OpenNLP Tokenizer annotator.
 * <p>
 * Mandatory parameters
 * <table border=1>
 * <caption></caption>
 * <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 * <tr><td>String</td> <td>opennlp.uima.ModelName</td> <td>The name of the model file</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.SentenceType</td> <td>The full name of the sentence type</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.TokenType</td> <td>The full name of the token type</td></tr>
 * </table>
 * <p>
 * Optional parameters
 * <table border=1>
 * <caption></caption>
 * <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 * <tr><td>String</td> <td>opennlp.uima.ProbabilityFeature</td> <td>The name of the double
 * probability feature (not set by default)</td></tr>
 * </table>
 *
 * @see TokenizerME
 */
public final class Tokenizer extends AbstractTokenizer {

  /**
   * The OpenNLP tokenizer.
   */
  private TokenizerME tokenizer;

  private Feature probabilityFeature;

  /**
   * Initializes a new instance.
   * <p>
   * Note: Use {@link #initialize(UimaContext) } to initialize
   * this instance. Not use the constructor.
   */
  public Tokenizer() {
    super("OpenNLP Tokenizer");

    // must not be implemented !
  }

  /**
   * Initializes the current instance with the given context.
   * <p>
   * Note: Do all initialization in this method, do not use the constructor.
   */
  public void initialize(UimaContext context)
      throws ResourceInitializationException {

    super.initialize(context);

    TokenizerModel model;

    try {
      TokenizerModelResource modelResource = (TokenizerModelResource) context
          .getResourceObject(UimaUtil.MODEL_PARAMETER);

      model = modelResource.getModel();
    } catch (ResourceAccessException e) {
      throw new ResourceInitializationException(e);
    }

    tokenizer = new TokenizerME(model);
  }

  /**
   * Initializes the type system.
   */
  public void typeSystemInit(TypeSystem typeSystem)
      throws AnalysisEngineProcessException {

    super.typeSystemInit(typeSystem);

    probabilityFeature = AnnotatorUtil
        .getOptionalFeatureParameter(context, tokenType,
            UimaUtil.PROBABILITY_FEATURE_PARAMETER, CAS.TYPE_NAME_DOUBLE);
  }


  @Override
  protected Span[] tokenize(CAS cas, AnnotationFS sentence) {
    return tokenizer.tokenizePos(sentence.getCoveredText());
  }

  @Override
  protected void postProcessAnnotations(Span[] tokens,
                                        AnnotationFS[] tokenAnnotations) {
    // if interest
    if (probabilityFeature != null) {
      double tokenProbabilties[] = tokenizer.getTokenProbabilities();

      for (int i = 0; i < tokenAnnotations.length; i++) {
        tokenAnnotations[i].setDoubleValue(probabilityFeature,
            tokenProbabilties[i]);
      }
    }
  }

  /**
   * Releases allocated resources.
   */
  public void destroy() {
    // dereference model to allow garbage collection
    tokenizer = null;
  }
}