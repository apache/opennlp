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

package opennlp.tools.sentiment;

/**
 * A {@link SentimentFactory} whose context generator adds emoji annotation features (see
 * {@link EmojiSentimentContextGenerator}). Pass it to
 * {@link SentimentME#train(String, opennlp.tools.util.ObjectStream,
 * opennlp.tools.util.TrainingParameters, SentimentFactory) SentimentME.train} to opt in; the
 * factory class is recorded in the trained model's manifest, so prediction re-creates the same
 * context. The default {@link SentimentFactory} is unchanged.
 */
public class EmojiSentimentFactory extends SentimentFactory {

  /**
   * Instantiates a factory whose context generator adds emoji annotation features.
   */
  public EmojiSentimentFactory() {
    super();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns an {@link EmojiSentimentContextGenerator}.</p>
   */
  @Override
  public SentimentContextGenerator createContextGenerator() {
    return new EmojiSentimentContextGenerator();
  }
}
