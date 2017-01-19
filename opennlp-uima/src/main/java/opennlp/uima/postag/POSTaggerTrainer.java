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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.apache.uima.util.ProcessTrace;

import opennlp.tools.ml.maxent.GIS;
import opennlp.tools.postag.POSDictionary;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerFactory;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.TrainingParameters;
import opennlp.uima.util.AnnotatorUtil;
import opennlp.uima.util.CasConsumerUtil;
import opennlp.uima.util.ContainingConstraint;
import opennlp.uima.util.OpennlpUtil;
import opennlp.uima.util.UimaUtil;

/**
 * OpenNLP POSTagger trainer.
 * <p>
 * Mandatory parameters
 * <table border=1>
 * <caption></caption>
 * <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 * <tr><td>String</td> <td>opennlp.uima.ModelName</td> <td>The name of the model file</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.SentenceType</td> <td>The full name of the sentence type</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.TokenType</td> <td>The full name of the token type</td></tr>
 * <tr><td>String</td> <td>pennlp.uima.POSFeature</td> <td>The name of the token pos feature,
 * the feature must be of type String</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.TagDictionaryName</td></tr>
 * </table>
 *
 * @deprecated will be removed after 1.7.1 release, there is no replacement
 */
@Deprecated

public class POSTaggerTrainer extends CasConsumer_ImplBase {

  public static final String TAG_DICTIONARY_NAME = "opennlp.uima.TagDictionaryName";

  private UimaContext mContext;

  private Type mSentenceType;

  private Type mTokenType;

  private String mModelName;

  private Feature mPOSFeature;

  private Logger mLogger;

  private List<POSSample> mPOSSamples = new ArrayList<>();

  private String language;

  private POSDictionary tagDictionary;

  /**
   * Initializes the current instance.
   */
  public void initialize() throws ResourceInitializationException {

    super.initialize();

    mContext = getUimaContext();

    mLogger = mContext.getLogger();

    if (mLogger.isLoggable(Level.INFO)) {
      mLogger.log(Level.INFO, "Initializing the OpenNLP " +
          "POSTagger trainer.");
    }

    mModelName = CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.MODEL_PARAMETER);

    language = CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.LANGUAGE_PARAMETER);

    String tagDictionaryName = CasConsumerUtil.getOptionalStringParameter(mContext,
        TAG_DICTIONARY_NAME);

    if (tagDictionaryName != null) {
      try (InputStream dictIn = AnnotatorUtil.getResourceAsStream(mContext, tagDictionaryName)) {
        tagDictionary = POSDictionary.create(dictIn);
      } catch (final IOException e) {
        // if this fails just print error message and continue
        final String message = "IOException during tag dictionary reading, "
            + "running without tag dictionary: " + e.getMessage();

        if (this.mLogger.isLoggable(Level.WARNING)) {
          this.mLogger.log(Level.WARNING, message);
        }
      }
    }
  }

  /**
   * Initialize the current instance with the given type system.
   */
  public void typeSystemInit(TypeSystem typeSystem)
      throws ResourceInitializationException {
    String sentenceTypeName = CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.SENTENCE_TYPE_PARAMETER);

    if (mLogger.isLoggable(Level.INFO)) {
      mLogger.log(Level.INFO, UimaUtil.SENTENCE_TYPE_PARAMETER + ": " +
          sentenceTypeName);
    }

    mSentenceType = CasConsumerUtil.getType(typeSystem, sentenceTypeName);

    String tokenTypeName = CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.TOKEN_TYPE_PARAMETER);

    mTokenType = CasConsumerUtil.getType(typeSystem, tokenTypeName);

    String posFeatureName = CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.POS_FEATURE_PARAMETER);

    mPOSFeature = mTokenType.getFeatureByBaseName(posFeatureName);
  }

  /**
   * Process the given CAS object.
   */
  public void processCas(CAS cas) {

    FSIndex<AnnotationFS> sentenceAnnotations = cas.getAnnotationIndex(mSentenceType);

    for (AnnotationFS sentence : sentenceAnnotations) {
      process(cas, sentence);
    }
  }

  private void process(CAS tcas, AnnotationFS sentence) {

    FSIndex<AnnotationFS> allTokens = tcas.getAnnotationIndex(mTokenType);

    ContainingConstraint containingConstraint =
        new ContainingConstraint(sentence);

    List<String> tokens = new ArrayList<>();
    List<String> tags = new ArrayList<>();

    Iterator<AnnotationFS> containingTokens = tcas.createFilteredIterator(
        allTokens.iterator(), containingConstraint);

    while (containingTokens.hasNext()) {

      AnnotationFS tokenAnnotation = containingTokens.next();

      String tag = tokenAnnotation.getFeatureValueAsString(mPOSFeature);

      tokens.add(tokenAnnotation.getCoveredText().trim());
      tags.add(tag);
    }

    mPOSSamples.add(new POSSample(tokens, tags));
  }

  /**
   * Called if the processing is finished, this method
   * does the training.
   */
  public void collectionProcessComplete(ProcessTrace trace)
      throws ResourceProcessException, IOException {

    GIS.PRINT_MESSAGES = false;

    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ITERATIONS_PARAM, Integer.toString(100));
    params.put(TrainingParameters.CUTOFF_PARAM, Integer.toString(5));

    POSModel posTaggerModel = POSTaggerME.train(language,
        ObjectStreamUtils.createObjectStream(mPOSSamples),
        params, new POSTaggerFactory(null, tagDictionary));

    // dereference to allow garbage collection
    mPOSSamples = null;

    File modelFile = new File(getUimaContextAdmin().getResourceManager()
        .getDataPath() + File.separatorChar + mModelName);

    OpennlpUtil.serialize(posTaggerModel, modelFile);
  }

  /**
   * The trainer is not stateless.
   */
  public boolean isStateless() {
    return false;
  }

  /**
   * Releases allocated resources.
   */
  public void destroy() {
    // dereference to allow garbage collection
    mPOSSamples = null;
  }
}
