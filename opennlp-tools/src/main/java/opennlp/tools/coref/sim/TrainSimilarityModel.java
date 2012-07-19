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

package opennlp.tools.coref.sim;

import java.io.IOException;

/**
 * Interface for training a similarity, gender, or number model.
 */
public interface TrainSimilarityModel {
  public void trainModel() throws IOException;
  /**
   * Creates simialrity training pairs based on the specified extents.
   * Extents are considered compatible is they are in the same coreference chain,
   * have the same named-entity tag, or share a common head word.  Incompatible extents are chosen at random
   * from the set of extents which don't meet this criteria.
   * @param extents
   */
  public void setExtents(Context[] extents);
}
