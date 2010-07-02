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

import opennlp.maxent.GIS;
import opennlp.model.AbstractModel;
import opennlp.model.MaxentModel;
import opennlp.model.TwoPassDataIndexer;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;

/**
 * Maxent implementation of {@link DocumentCategorizer}.
 */
public class DocumentCategorizerME implements DocumentCategorizer {

  MaxentModel mModel;
  private DocumentCategorizerContextGenerator mContextGenerator;

  /**
   * Initializes the current instance with the given {@link MaxentModel}.
   *
   * @param model
   */
  public DocumentCategorizerME(MaxentModel model) {
    this(model, new FeatureGenerator[]{new BagOfWordsFeatureGenerator()});
  }

  /**
   * Initializes the current instance with a the given {@link MaxentModel}
   * and {@link FeatureGenerator}s.
   *
   * @param model
   * @param featureGenerators
   */
  public DocumentCategorizerME(MaxentModel model,
      FeatureGenerator... featureGenerators) {

    mModel = model;
    mContextGenerator =
        new DocumentCategorizerContextGenerator(featureGenerators);
  }

  /**
   * Categorizes the given text.
   *
   * @param text
   */
  public double[] categorize(String text[]) {
    return mModel.eval(mContextGenerator.getContext(text));
  }

  public double[] categorize(String documentText) {
    Tokenizer tokenizer = SimpleTokenizer.INSTANCE;
    return categorize(tokenizer.tokenize(documentText));
  }

  public String getBestCategory(double[] outcome) {
    return mModel.getBestOutcome(outcome);
  }

  public int getIndex(String category) {
    return mModel.getIndex(category);
  }

  public String getCategory(int index) {
    return mModel.getOutcome(index);
  }

  public int getNumberOfCategories() {
    return mModel.getNumOutcomes();
  }

  public String getAllResults(double results[]) {
    return mModel.getAllOutcomes(results);
  }

  /**
   * Trains a new model for the {@link DocumentCategorizerME}.
   *
   * @param eventStream
   *
   * @return the new model
   */
  public static AbstractModel train(DocumentCategorizerEventStream eventStream) throws IOException {
    return GIS.trainModel(100, new TwoPassDataIndexer(eventStream, 5));
  }
}
