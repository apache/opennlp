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


package opennlp.tools.util.featuregen;

/**
 * The {@link FactoryResourceManager} is responsible to provide resources
 * during construction of the {@link AdaptiveFeatureGenerator} set by the
 * {@link Factory}.
 */
public interface FactoryResourceManager {

  /**
   * Looks up the resource for the provided key.
   *
   * @param key - the key identifies the resource
   *
   * @return the resource
   */
  Object getResource(String key);
}
