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

import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.coref.DiscourseModel;
import opennlp.tools.coref.mention.MentionContext;

/** 
 * Interface for coreference resolvers. 
 */
public interface Resolver {

  /** 
   * Returns true if this resolver is able to resolve the referring expression of the same type
   * as the specified mention.
   * 
   * @param mention The mention being considered for resolution.
   * 
   * @return true if the resolver handles this type of referring
   * expression, false otherwise.
   */
  public boolean canResolve(MentionContext mention);

  /** 
   * Resolve this referring expression to a discourse entity in the discourse model.
   * 
   * @param ec the referring expression.
   * @param dm the discourse model.
   * 
   * @return the discourse entity which the resolver believes this
   * referring expression refers to or null if no discourse entity is
   * coreferent with the referring expression.
   */
  public DiscourseEntity resolve(MentionContext ec, DiscourseModel dm);

  /** 
   * Uses the specified mention and discourse model to train this resolver.
   * All mentions sent to this method need to have their id fields set to indicate coreference
   * relationships.
   * 
   * @param mention The mention which is being used for training.
   * @param model the discourse model.
   * 
   * @return the discourse entity which is referred to by the referring
   * expression or null if no discourse entity is referenced.
   */
  public DiscourseEntity retain(MentionContext mention, DiscourseModel model);

  /** 
   * Retrains model on examples for which retain was called.
   * 
   * @throws IOException
   */
  public void train() throws IOException;
}
