/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.sentiment;

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.SequenceCodec;
import opennlp.tools.util.ext.ExtensionLoader;

/**
 * Class for creating sentiment factories for training.
 */
public class SentimentFactory extends BaseToolFactory {

  private static final String TOKENIZER_NAME = "sentiment.tokenizer";

  private Tokenizer tokenizer;

  /**
   * Validates the artifact map --> nothing to validate.
   */
  @Override
  public void validateArtifactMap() throws InvalidFormatException {
    // nothing to validate
  }

  /**
   * Creates a new context generator.
   *
   * @return a context generator for Sentiment Analysis
   */
  public SentimentContextGenerator createContextGenerator() {
    return new SentimentContextGenerator();
  }

  /**
   * Returns the tokenizer
   *
   * @return the tokenizer
   */
  public Tokenizer getTokenizer() {
    if (this.tokenizer == null) {
      if (artifactProvider != null) {
        String className = artifactProvider.getManifestProperty(TOKENIZER_NAME);
        if (className != null) {
          this.tokenizer = ExtensionLoader.instantiateExtension(Tokenizer.class,
              className);
        }
      }
      if (this.tokenizer == null) { // could not load using artifact provider
        this.tokenizer = WhitespaceTokenizer.INSTANCE;
      }
    }
    return tokenizer;
  }

}
