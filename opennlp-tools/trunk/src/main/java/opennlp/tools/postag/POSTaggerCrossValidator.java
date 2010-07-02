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
import opennlp.tools.util.ObjectStreamException;
import opennlp.tools.util.eval.CrossValidationPartitioner;
import opennlp.tools.util.eval.Mean;

public class POSTaggerCrossValidator {

  private POSDictionary tagDictionary;
  private Dictionary ngramDictionary;
  private int cutoff;
  
  private Mean wordAccuracy;
  
  public POSTaggerCrossValidator(POSDictionary tagDictionary,
      Dictionary ngramDictionary, int cutoff) {
    this.tagDictionary = tagDictionary;
    this.ngramDictionary = ngramDictionary;
    this.cutoff = cutoff;
  }
  
  public void evaluate(ObjectStream<POSSample> samples, int nFolds)
    throws ObjectStreamException, IOException {
    CrossValidationPartitioner<POSSample> partitioner = 
      new CrossValidationPartitioner<POSSample>(samples, nFolds);
  
     while (partitioner.hasNext()) {
       
       CrossValidationPartitioner.TrainingSampleStream<POSSample> trainingSampleStream =
         partitioner.next();
       
       POSModel model = POSTaggerTrainer.train(trainingSampleStream, tagDictionary,
           ngramDictionary, cutoff);
       
       POSEvaluator evaluator = new POSEvaluator(new POSTaggerME(model));
       evaluator.evaluate(trainingSampleStream.getTestSampleStream());
       
       wordAccuracy.add(evaluator.getWordAccuracy(), evaluator.getWordCount());
     }
  }
  
  /**
   * Retrieves the accuracy for all iterations.
   * 
   * @return
   */
  public double getWordAccuracy() {
    return wordAccuracy.mean();
  }
  
  /**
   * Retrieves the number of words which where validated
   * over all iterations. The result is the amount of folds
   * multiplied by the total number of words.
   * 
   * @return
   */
  public long getWordCount() {
    return wordAccuracy.count();
  }
}
