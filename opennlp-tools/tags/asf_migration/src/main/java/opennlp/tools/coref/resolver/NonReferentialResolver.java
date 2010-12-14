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

package opennlp.tools.coref.resolver;

import java.io.IOException;

import opennlp.tools.coref.mention.MentionContext;

/**
 * Provides the interface for a object to provide a resolver with a non-referential
 * probability.  Non-referential resolvers compute the probability that a particular mention refers
 * to no antecedent.  This probability can then compete with the probability that
 * a mention refers with a specific antecedent.
 */
public interface NonReferentialResolver {
  
  /**
   * Returns the probability that the specified mention doesn't refer to any previous mention.
   * 
   * @param mention The mention under consideration.
   * @return A probability that the specified mention doesn't refer to any previous mention.
   */
  public double getNonReferentialProbability(MentionContext mention);

  /**
   * Designates that the specified mention be used for training.
   * 
   * @param mention The mention to be used.  The mention id is used to determine
   * whether this mention is referential or non-referential.
   */
  public void addEvent(MentionContext mention);

  /**
   * Trains a model based on the events given to this resolver via #addEvent.
   * 
   * @throws IOException When the model can not be written out.
   */
  public void train() throws IOException;
}
