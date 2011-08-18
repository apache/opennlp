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

import java.io.IOException;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.CrossValidationPartitioner;
import opennlp.tools.util.eval.Mean;
import opennlp.tools.util.eval.EvaluationSampleListener;
import opennlp.tools.util.model.ModelType;

public class POSTaggerCrossValidator {

  private final String languageCode;
  private final ModelType modelType;
  private final int cutoff;
  private final int iterations;
  
  private final TrainingParameters params;
  
  private POSDictionary tagDictionary;
  private Dictionary ngramDictionary;

  private Mean wordAccuracy = new Mean();
  
  
  public POSTaggerCrossValidator(String languageCode, ModelType modelType, POSDictionary tagDictionary,
      Dictionary ngramDictionary, int cutoff, int iterations) {
    this.languageCode = languageCode;
    this.modelType = modelType;
    this.cutoff = cutoff;
    this.iterations = iterations;
    this.tagDictionary = tagDictionary;
    this.ngramDictionary = ngramDictionary;
    
    params = null;
  }
  
  public POSTaggerCrossValidator(String languageCode, ModelType modelType, POSDictionary tagDictionary,
      Dictionary ngramDictionary) {
    this(languageCode, modelType, tagDictionary, ngramDictionary, 5, 100);
  }
  
  public POSTaggerCrossValidator(String languageCode,
      TrainingParameters trainParam, POSDictionary tagDictionary,
      Dictionary ngramDictionary) {
    this.params = trainParam;
    this.languageCode = languageCode;
    cutoff = -1;
    iterations = -1;
    modelType = null;
  }
  
  
  /**
   * Starts the evaluation.
   * 
   * @param samples
   *          the data to train and test
   * @param nFolds
   *          number of folds
   * 
   * @throws IOException
   */
  public void evaluate(ObjectStream<POSSample> samples, int nFolds)
      throws IOException, IOException {
    evaluate(samples, nFolds, null);
  }

  /**
   * Starts the evaluation.
   * 
   * @param samples
   *          the data to train and test
   * @param nFolds
   *          number of folds
   * @param listener
   *          an optional listener to print missclassified items
   * 
   * @throws IOException
   */
  public void evaluate(ObjectStream<POSSample> samples, int nFolds,
      EvaluationSampleListener<POSSample> listener) throws IOException, IOException {
    
    CrossValidationPartitioner<POSSample> partitioner = new CrossValidationPartitioner<POSSample>(
        samples, nFolds);

    while (partitioner.hasNext()) {

      CrossValidationPartitioner.TrainingSampleStream<POSSample> trainingSampleStream = partitioner
          .next();

      POSModel model;

      if (params == null) {
        model = POSTaggerME.train(languageCode, trainingSampleStream,
            modelType, tagDictionary, ngramDictionary, cutoff, iterations);
      } else {
        model = POSTaggerME.train(languageCode, trainingSampleStream, params,
            this.tagDictionary, this.ngramDictionary);
      }

      POSEvaluator evaluator = new POSEvaluator(new POSTaggerME(model));
      evaluator.addListener(listener);
      
      evaluator.evaluate(trainingSampleStream.getTestSampleStream());

      wordAccuracy.add(evaluator.getWordAccuracy(), evaluator.getWordCount());
    }
  }
  
  /**
   * Retrieves the accuracy for all iterations.
   * 
   * @return the word accuracy
   */
  public double getWordAccuracy() {
    return wordAccuracy.mean();
  }
  
  /**
   * Retrieves the number of words which where validated
   * over all iterations. The result is the amount of folds
   * multiplied by the total number of words.
   * 
   * @return the word count
   */
  public long getWordCount() {
    return wordAccuracy.count();
  }
}
