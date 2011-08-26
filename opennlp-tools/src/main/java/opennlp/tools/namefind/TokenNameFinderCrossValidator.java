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

package opennlp.tools.namefind;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.CrossValidationPartitioner;
import opennlp.tools.util.eval.FMeasure;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;
import opennlp.tools.util.model.ModelUtil;

public class TokenNameFinderCrossValidator {

  private final String languageCode;
  private final TrainingParameters params;
  private final String type;
  private final byte[] featureGeneratorBytes;
  private final Map<String, Object> resources;
  private TokenNameFinderEvaluationMonitor[] listeners;
  

  private FMeasure fmeasure = new FMeasure();

  /**
   * Name finder cross validator
   *  
   * @param languageCode 
   *          the language of the training data
   * @param cutoff
   * @param iterations
   * 
   * @deprecated use {@link #TokenNameFinderCrossValidator(String, String, TrainingParameters, byte[], Map, TokenNameFinderEvaluationMonitor...)}
   * instead and pass in a TrainingParameters object.
   */
  @Deprecated  
  public TokenNameFinderCrossValidator(String languageCode, int cutoff,
      int iterations) {
    this(languageCode, null, cutoff, iterations);
  }

  /**
   * Name finder cross validator
   * 
   * @param languageCode
   *          the language of the training data
   * @param type
   *          null or an override type for all types in the training data
   * @param cutoff
   *          specifies the min number of times a feature must be seen
   * @param iterations
   *          the number of iterations
   *          
   * @deprecated use {@link #TokenNameFinderCrossValidator(String, String, TrainingParameters, byte[], Map, TokenNameFinderEvaluationMonitor...)}
   * instead and pass in a TrainingParameters object.
   */
  @Deprecated
  public TokenNameFinderCrossValidator(String languageCode, String type,
      int cutoff, int iterations) {
    this.languageCode = languageCode;
    this.type = type;
    
    this.params = ModelUtil.createTrainingParameters(iterations, cutoff);
    this.featureGeneratorBytes = null;
    this.resources = Collections.<String, Object>emptyMap(); 
  }

  /**
   * Name finder cross validator
   * 
   * @param languageCode
   *          the language of the training data
   * @param type
   *          null or an override type for all types in the training data
   * @param featureGeneratorBytes
   *          descriptor to configure the feature generation or null
   * @param resources
   *          the resources for the name finder or null if none
   * @param cutoff
   *          specifies the min number of times a feature must be seen
   * @param iterations
   *          the number of iterations
   *          
   * @deprecated use {@link #TokenNameFinderCrossValidator(String, String, TrainingParameters, byte[], Map, TokenNameFinderEvaluationMonitor...)}
   * instead and pass in a TrainingParameters object.
   */
  @Deprecated
  public TokenNameFinderCrossValidator(String languageCode, String type,
      byte[] featureGeneratorBytes,
      Map<String, Object> resources, int iterations, int cutoff) {
    this.languageCode = languageCode;
    this.type = type;
    this.featureGeneratorBytes = featureGeneratorBytes;
    this.resources = resources;
    
    this.params = ModelUtil.createTrainingParameters(iterations, cutoff);;
  }

  /**
   * Name finder cross validator
   * 
   * @param languageCode
   *          the language of the training data
   * @param type
   *          null or an override type for all types in the training data
   * @param trainParams
   *          machine learning train parameters
   * @param featureGeneratorBytes
   *          descriptor to configure the feature generation or null
   * @param listeners
   *          a list of listeners
   * @param resources
   *          the resources for the name finder or null if none
   */
  public TokenNameFinderCrossValidator(String languageCode, String type,
      TrainingParameters trainParams, byte[] featureGeneratorBytes,
      Map<String, Object> resources,
      TokenNameFinderEvaluationMonitor... listeners) {
    
    this.languageCode = languageCode;
    this.type = type;
    this.featureGeneratorBytes = featureGeneratorBytes;
    this.resources = resources;

    this.params = trainParams;
    
    this.listeners = listeners;
  }

  /**
   * Starts the evaluation.
   * 
   * @param samples
   *          the data to train and test
   * @param nFolds
   *          number of folds
   * @throws IOException
   */
  public void evaluate(ObjectStream<NameSample> samples, int nFolds)
      throws IOException {
    CrossValidationPartitioner<NameSample> partitioner = new CrossValidationPartitioner<NameSample>(
        samples, nFolds);

    while (partitioner.hasNext()) {

      CrossValidationPartitioner.TrainingSampleStream<NameSample> trainingSampleStream = partitioner
          .next();

      TokenNameFinderModel model  = opennlp.tools.namefind.NameFinderME.train(languageCode, type,
            trainingSampleStream, params, featureGeneratorBytes, resources);

      // do testing
      TokenNameFinderEvaluator evaluator = new TokenNameFinderEvaluator(
          new NameFinderME(model), listeners);

      evaluator.evaluate(trainingSampleStream.getTestSampleStream());

      fmeasure.mergeInto(evaluator.getFMeasure());
    }
  }

  public FMeasure getFMeasure() {
    return fmeasure;
  }
}
