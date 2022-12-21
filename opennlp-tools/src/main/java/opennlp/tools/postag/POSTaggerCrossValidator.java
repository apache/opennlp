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

package opennlp.tools.postag;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.CrossValidationPartitioner;
import opennlp.tools.util.eval.Mean;

public class POSTaggerCrossValidator {

  private final String languageCode;

  private final TrainingParameters params;

  private byte[] featureGeneratorBytes;
  private Map<String, Object> resources;

  private final Mean wordAccuracy = new Mean();
  private final POSTaggerEvaluationMonitor[] listeners;

  /* this will be used to load the factory after the ngram dictionary was created */
  private String factoryClassName;
  /* user can also send a ready to use factory */
  private POSTaggerFactory factory;

  private final Integer tagdicCutoff;
  private File tagDictionaryFile;

  /**
   * Initializes a {@link POSTaggerCrossValidator} that builds a ngram dictionary
   * dynamically. It instantiates a subclass of {@link POSTaggerFactory} using
   * the tag and the ngram dictionaries.
   *
   * @param languageCode An ISO conform language code.
   * @param trainParam The {@link TrainingParameters} for the context of cross validation.
   * @param tagDictionary The {@link File} that references the a {@link TagDictionary}.
   * @param featureGeneratorBytes The bytes for feature generation.
   * @param resources  Additional resources as key-value map.
   * @param factoryClass The class name used for factory instantiation.
   * @param listeners The {@link POSTaggerEvaluationMonitor evaluation listeners}.
   */
  public POSTaggerCrossValidator(String languageCode,
                                 TrainingParameters trainParam, File tagDictionary,
                                 byte[] featureGeneratorBytes, Map<String, Object> resources,
                                 Integer tagdicCutoff, String factoryClass,
                                 POSTaggerEvaluationMonitor... listeners) {
    this.languageCode = languageCode;
    this.params = trainParam;
    this.featureGeneratorBytes = featureGeneratorBytes;
    this.resources = resources;
    this.listeners = listeners;
    this.factoryClassName = factoryClass;
    this.tagdicCutoff = tagdicCutoff;
    this.tagDictionaryFile = tagDictionary;
  }


  /**
   * Creates a {@link POSTaggerCrossValidator} using the given {@link POSTaggerFactory}.
   *
   * @param languageCode An ISO conform language code.
   * @param trainParam The {@link TrainingParameters} for the context of cross validation.
   * @param factory The {@link POSTaggerFactory} to be used.
   * @param listeners The {@link POSTaggerEvaluationMonitor evaluation listeners}.
   */
  public POSTaggerCrossValidator(String languageCode,
      TrainingParameters trainParam, POSTaggerFactory factory,
      POSTaggerEvaluationMonitor... listeners) {
    this.languageCode = languageCode;
    this.params = trainParam;
    this.listeners = listeners;
    this.factory = factory;
    this.tagdicCutoff = null;
  }

  /**
   * Starts the evaluation.
   *
   * @param samples The {@link ObjectStream} of {@link POSSample samples} to train and test with.
   * @param nFolds Number of folds. It must be greater than zero.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public void evaluate(ObjectStream<POSSample> samples, int nFolds) throws IOException {

    CrossValidationPartitioner<POSSample> partitioner = new CrossValidationPartitioner<>(
        samples, nFolds);

    while (partitioner.hasNext()) {

      CrossValidationPartitioner.TrainingSampleStream<POSSample> trainingSampleStream = partitioner
          .next();

      if (this.tagDictionaryFile != null
          && this.factory.getTagDictionary() == null) {
        this.factory.setTagDictionary(this.factory
            .createTagDictionary(tagDictionaryFile));
      }

      TagDictionary dict = null;
      if (this.tagdicCutoff != null) {
        dict = this.factory.getTagDictionary();
        if (dict == null) {
          dict = this.factory.createEmptyTagDictionary();
        }
        if (dict instanceof MutableTagDictionary) {
          POSTaggerME.populatePOSDictionary(trainingSampleStream, (MutableTagDictionary)dict,
              this.tagdicCutoff);
        } else {
          throw new IllegalArgumentException(
              "Can't extend a TagDictionary that does not implement MutableTagDictionary.");
        }
        trainingSampleStream.reset();
      }

      if (this.factory == null) {
        this.factory = POSTaggerFactory.create(this.factoryClassName, null, null);
      }

      factory.init(featureGeneratorBytes, resources, dict);

      POSModel model = POSTaggerME.train(languageCode, trainingSampleStream,
          params, this.factory);

      POSEvaluator evaluator = new POSEvaluator(new POSTaggerME(model), listeners);

      evaluator.evaluate(trainingSampleStream.getTestSampleStream());

      wordAccuracy.add(evaluator.getWordAccuracy(), evaluator.getWordCount());

      if (this.tagdicCutoff != null) {
        this.factory.setTagDictionary(null);
      }
    }
  }

  /**
   * @return Retrieves the accuracy for all iterations.
   */
  public double getWordAccuracy() {
    return wordAccuracy.mean();
  }

  /**
   * @return Retrieves the number of words which where validated
   *         over all iterations. The result is the amount of folds
   *         multiplied by the total number of words.
   */
  public long getWordCount() {
    return wordAccuracy.count();
  }
  
}
