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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.CrossValidationPartitioner;
import opennlp.tools.util.eval.EvaluationMonitor;
import opennlp.tools.util.eval.Mean;
import opennlp.tools.util.model.ModelType;
import opennlp.tools.util.model.ModelUtil;

public class POSTaggerCrossValidator {

  private final String languageCode;
  
  private final TrainingParameters params;
  
  private POSDictionary tagDictionary;
  private Dictionary ngramDictionary;
  private Integer ngramCutoff;

  private Mean wordAccuracy = new Mean();
  private List<? extends EvaluationMonitor<POSSample>> listeners;
  
  
  public POSTaggerCrossValidator(String languageCode, ModelType modelType, POSDictionary tagDictionary,
      Dictionary ngramDictionary, int cutoff, int iterations) {
    this.languageCode = languageCode;
    this.params = ModelUtil.createTrainingParameters(iterations, cutoff);
    this.params.put(TrainingParameters.ALGORITHM_PARAM, modelType.toString());
    this.tagDictionary = tagDictionary;
    this.ngramDictionary = ngramDictionary;
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
  }

  public POSTaggerCrossValidator(String languageCode,
      TrainingParameters trainParam, POSDictionary tagDictionary,
      Integer ngramCutoff, EvaluationMonitor<POSSample> listener) {
    this(languageCode, trainParam, tagDictionary, null, Collections
        .singletonList(listener));
    this.ngramCutoff = ngramCutoff;
    if (listeners != null) {
      this.listeners = new LinkedList<EvaluationMonitor<POSSample>>(listeners);
    }
  }
  
  public POSTaggerCrossValidator(String languageCode,
      TrainingParameters trainParam, POSDictionary tagDictionary,
      Dictionary ngramDictionary,
      List<? extends EvaluationMonitor<POSSample>> listeners) {
    this(languageCode, trainParam, tagDictionary, ngramDictionary);
    if (listeners != null) {
      this.listeners = new LinkedList<EvaluationMonitor<POSSample>>(
          listeners);
    }
  }

  public POSTaggerCrossValidator(String languageCode,
      TrainingParameters trainParam, POSDictionary tagDictionary,
      Dictionary ngramDictionary, EvaluationMonitor<POSSample> listener) {
    this(languageCode, trainParam, tagDictionary, ngramDictionary, Collections
        .singletonList(listener));
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
  public void evaluate(ObjectStream<POSSample> samples, int nFolds) throws IOException, IOException {
    
    CrossValidationPartitioner<POSSample> partitioner = new CrossValidationPartitioner<POSSample>(
        samples, nFolds);

    while (partitioner.hasNext()) {

      CrossValidationPartitioner.TrainingSampleStream<POSSample> trainingSampleStream = partitioner
          .next();
      
      Dictionary ngramDict = null;
      if (this.ngramDictionary == null) {
        if(this.ngramCutoff != null) {
          System.err.print("Building ngram dictionary ... ");
          try {
            ngramDict = POSTaggerME.buildNGramDictionary(trainingSampleStream,
                this.ngramCutoff);
            trainingSampleStream.reset();
          } catch (IOException e) {
            CmdLineUtil.printTrainingIoError(e);
            throw new TerminateToolException(-1);
          }
          System.err.println("done");
        }
      } else {
        ngramDict = this.ngramDictionary;
      }

      POSModel model = POSTaggerME.train(languageCode, trainingSampleStream, params,
            this.tagDictionary, ngramDict);

      POSEvaluator evaluator = new POSEvaluator(new POSTaggerME(model), listeners);
      
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
