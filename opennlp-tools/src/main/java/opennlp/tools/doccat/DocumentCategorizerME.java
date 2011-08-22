/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

import opennlp.maxent.GIS;
import opennlp.model.AbstractModel;
import opennlp.model.MaxentModel;
import opennlp.model.TrainUtil;
import opennlp.model.TwoPassDataIndexer;
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
   * Initializes the current instance with the given {@link MaxentModel}.
   *
   * @param model
   * 
   * @deprecated Use {@link DocumentCategorizerME#DocumentCategorizerME(DoccatModel)} instead.
   */
  @Deprecated
  public DocumentCategorizerME(MaxentModel model) {
    this(model, new FeatureGenerator[]{new BagOfWordsFeatureGenerator()});
  }

  /**
   * Initializes the current instance with a the given {@link MaxentModel}
   * and {@link FeatureGenerator}s.
   *
   * @param model
   * @param featureGenerators
   * 
   * @deprecated Use {@link DocumentCategorizerME#DocumentCategorizerME(DoccatModel, FeatureGenerator...)} instead.
   */
  @Deprecated
  public DocumentCategorizerME(MaxentModel model,
      FeatureGenerator... featureGenerators) {

    this.model = model;
    mContextGenerator =
        new DocumentCategorizerContextGenerator(featureGenerators);
  }

  /**
   * Categorizes the given text.
   *
   * @param text
   */
  public double[] categorize(String text[]) {
    return model.eval(mContextGenerator.getContext(text));
  }

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

  /**
   * Trains a new model for the {@link DocumentCategorizerME}.
   *
   * @param eventStream
   *
   * @return the new model
   */
   @Deprecated
  public static AbstractModel train(DocumentCategorizerEventStream eventStream) throws IOException {
    return GIS.trainModel(100, new TwoPassDataIndexer(eventStream, 5));
  }
  
   
   public static DoccatModel train(String languageCode, ObjectStream<DocumentSample> samples,
       TrainingParameters mlParams, FeatureGenerator... featureGenerators)
   throws IOException {
     
     Map<String, String> manifestInfoEntries = new HashMap<String, String>();
     
     AbstractModel model = TrainUtil.train(
         new DocumentCategorizerEventStream(samples, featureGenerators),
         mlParams.getSettings(), manifestInfoEntries);
       
     return new DoccatModel(languageCode, model, manifestInfoEntries);
   }
   
  /**
   * Trains a document categorizer model with custom feature generation.
   * 
   * @param languageCode
   * @param samples
   * @param cutoff
   * @param iterations
   * @param featureGenerators
   * 
   * @return the trained doccat model
   * 
   * @throws IOException
   */
  public static DoccatModel train(String languageCode, ObjectStream<DocumentSample> samples, int cutoff, int iterations, FeatureGenerator... featureGenerators)
      throws IOException {
    return train(languageCode, samples, ModelUtil.createTrainingParameters(iterations, cutoff),
        featureGenerators);
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
  public static DoccatModel train(String languageCode, ObjectStream<DocumentSample> samples, int cutoff, int iterations) throws IOException {
    return train(languageCode, samples, cutoff, iterations, defaultFeatureGenerator);
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
    return train(languageCode, samples, 5, 100, defaultFeatureGenerator);
  }
}
