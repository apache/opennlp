/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.maxent;

import opennlp.model.MaxentModel;

/**
 * A object to facilitate the resetting of a MaxentModel variable to a
 * new value (model).  In general this will be used anonymously, for example, as
 * follows: 
 * <p>
 *     <pre>
 *     private final ModelReplacementManager replacementManager =
 *	  new ModelReplacementManager(
 *	      new ModelSetter() {
 *		  public void setModel(MaxentModel m) {
 *		      model = m;
 *		  }
 *	      }
 *	  );
 *     </pre>
 * <p>
 * where "model" would be the actual variable name of the model used by your
 * application which you wish to be able to swap (you might have other models
 * which need their own ModelSetters).
 *
 * <p>
 * Basically, this is just a clean way of giving a ModelReplacementManager
 * access to a private variable holding the model.  Nothing complex here.
 */
public interface ModelSetter {

  /**
   * Assign a new MaxentModel value to a MaxentModel variable.
   * 
   * @param m
   *          The new model.
   */
  public void setModel(MaxentModel m);
}
