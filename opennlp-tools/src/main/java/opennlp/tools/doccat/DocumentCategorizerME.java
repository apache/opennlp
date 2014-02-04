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


package opennlp.tools.doccat;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.TrainUtil;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelUtil;

/**
 * Maxent implementation of {@link DocumentCategorizer}.
 */
public class DocumentCategorizerME implements DocumentCategorizer {

  /**
   * Shared default thread safe feature generator.
   */
  private static FeatureGenerator defaultFeatureGenerator = new BagOfWordsFeatureGenerator();
  
  private MaxentModel model;
  private DocumentCategorizerContextGenerator mContextGenerator;

  /**
   * Initializes a the current instance with a doccat model and custom feature generation.
   * The feature generation must be identical to the configuration at training time.
   * 
   * @param model
   * @param featureGenerators
   */
  public DocumentCategorizerME(DoccatModel model, FeatureGenerator... featureGenerators) {
    this.model = model.getChunkerModel();
    this.mContextGenerator = new DocumentCategorizerContextGenerator(featureGenerators);
  }
  
  /**
   * Initializes the current instance with a doccat model. Default feature generation is used.
   * 
   * @param model
   */
  public DocumentCategorizerME(DoccatModel model) {
    this(model, defaultFeatureGenerator);
  }

  /**
   * Categorizes the given text.
   *
   * @param text
   */
  public double[] categorize(String text[]) {
    return model.eval(mContextGenerator.getContext(text));
  }

  /**
   * Categorizes the given text. The text is tokenized with the SimpleTokenizer before it
   * is passed to the feature generation.
   */
  public double[] categorize(String documentText) {
    Tokenizer tokenizer = SimpleTokenizer.INSTANCE;
    return categorize(tokenizer.tokenize(documentText));
  }

  public String getBestCategory(double[] outcome) {
    return model.getBestOutcome(outcome);
  }

  public int getIndex(String category) {
    return model.getIndex(category);
  }

  public String getCategory(int index) {
    return model.getOutcome(index);
  }

  public int getNumberOfCategories() {
    return model.getNumOutcomes();
  }

  public String getAllResults(double results[]) {
    return model.getAllOutcomes(results);
  }

   public static DoccatModel train(String languageCode, ObjectStream<DocumentSample> samples,
       TrainingParameters mlParams, FeatureGenerator... featureGenerators)
   throws IOException {
     
     if (featureGenerators.length == 0) {
       featureGenerators = new FeatureGenerator[]{defaultFeatureGenerator};
     }
     
     Map<String, String> manifestInfoEntries = new HashMap<String, String>();
     
     MaxentModel model = TrainUtil.train(
         new DocumentCategorizerEventStream(samples, featureGenerators),
         mlParams.getSettings(), manifestInfoEntries);
       
     return new DoccatModel(languageCode, model, manifestInfoEntries);
   }
  
  /**
   * Trains a doccat model with default feature generation.
   * 
   * @param languageCode
   * @param samples
   * 
   * @return the trained doccat model
   * 
   * @throws IOException
   * @throws ObjectStreamException 
   */
  public static DoccatModel train(String languageCode, ObjectStream<DocumentSample> samples) throws IOException {
    return train(languageCode, samples, ModelUtil.createDefaultTrainingParameters(), defaultFeatureGenerator);
  }
}
