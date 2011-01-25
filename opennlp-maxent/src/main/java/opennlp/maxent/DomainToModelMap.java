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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import opennlp.model.MaxentModel;

/**
 * A class which stores a mapping from ModelDomain objects to MaxentModels.
 * This permits an application to replace an old model for a domain with a
 * newly trained one in a thread-safe manner.  By calling the getModel()
 * method, the application can create new instances of classes which use the
 * relevant models.
 */
public class DomainToModelMap {

  // the underlying object which stores the mapping
  private Map map = Collections.synchronizedMap(new HashMap());

  /**
   * Sets the model for the given domain.
   * 
   * @param domain
   *          The ModelDomain object which keys to the model.
   * @param model
   *          The MaxentModel trained for the domain.
   */
  public void setModelForDomain(ModelDomain domain, MaxentModel model) {
    map.put(domain, model);
  }

  /**
   * Get the model mapped to by the given ModelDomain key.
   * 
   * @param domain
   *          The ModelDomain object which keys to the desired model.
   * @return The MaxentModel corresponding to the given domain.
   */
  public MaxentModel getModel(ModelDomain domain) {
    if (map.containsKey(domain)) {
      return (MaxentModel) map.get(domain);
    } else {
      throw new NoSuchElementException("No model has been created for "
          + "domain: " + domain);
    }
  }

  /**
   * Removes the mapping for this ModelDomain key from this map if present.
   * 
   * @param domain
   *          The ModelDomain key whose mapping is to be removed from the map.
   */
  public void removeDomain(ModelDomain domain) {
    map.remove(domain);
  }

  /**
   * A set view of the ModelDomain keys contained in this map.
   * 
   * @return a set view of the ModelDomain keys contained in this map
   */
  public Set keySet() {
    return map.keySet();
  }

}
