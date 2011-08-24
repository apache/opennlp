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

package opennlp.tools.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class TrainingParameters {
  
  public static final String ALGORITHM_PARAM = "Algorithm";
  
  public static final String ITERATIONS_PARAM = "Iterations";
  public static final String CUTOFF_PARAM = "Cutoff";
  
  private Map<String, String> parameters = new HashMap<String, String>();
  
  public TrainingParameters() {
  }
  
  public TrainingParameters(InputStream in) throws IOException {
    
    Properties properties = new Properties();
    properties.load(in);

    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
      parameters.put((String) entry.getKey(), (String) entry.getValue());
    }
  }
  
  /**
   * Retrieves the training algorithm name for a given name space.
   * 
   * @return the name or null if not set.
   */
  public String algorithm(String namespace) {
    return parameters.get(namespace + "." + ALGORITHM_PARAM);
  }
  
  /**
   * Retrieves the training algorithm name.
   * 
   * @return the name or null if not set.
   */
  public String algorithm() {
    return parameters.get(ALGORITHM_PARAM);
  }
  
  /**
   * Retrieves a map with the training parameters which have the passed name space.
   * 
   * @param namespace
   * 
   * @return a parameter map which can be passed to the train and validate methods.
   */
  public Map<String, String> getSettings(String namespace) {
    
    Map<String, String> trainingParams = new HashMap<String, String>();
    
    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      String key = entry.getKey();

      if (namespace != null) {
        String prefix = namespace + ".";
        
        if (key.startsWith(prefix))  {
          trainingParams.put(key.substring(prefix.length()), entry.getValue());
        }
      }
      else {
        if (!key.contains(".")) {
          trainingParams.put(key, entry.getValue());
        }
      }
    }
    
    return Collections.unmodifiableMap(trainingParams);
  }
  
  /** 
   * Retrieves all parameters without a name space.
   * 
   * @return the settings map
   */
  public Map<String, String> getSettings() {
    return getSettings(null);
  }
  
  // reduces the params to contain only the params in the name space
  public TrainingParameters getParameters(String namespace) {
    
    TrainingParameters params = new TrainingParameters();
    
    for (Map.Entry<String, String> entry : getSettings(namespace).entrySet()) {
      params.put(entry.getKey(), entry.getValue());
    }
    
    return params;
  }
  
  public void put(String namespace, String key, String value) {
    
    if (namespace == null) {
      parameters.put(key, value);
    }
    else {
      parameters.put(namespace + "." + key, value);
    }
  }
  
  public void put(String key, String value) {
    put(null, key, value);
  }
  
  public void serialize(OutputStream out) throws IOException {
    Properties properties = new Properties();
    
    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      properties.put(entry.getKey(), entry.getValue());
    }
    
    properties.store(out, null);
  }
}
