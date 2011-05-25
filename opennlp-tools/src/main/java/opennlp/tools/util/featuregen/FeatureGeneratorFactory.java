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
 * The {@link FeatureGeneratorFactory} interface is factory for {@link AdaptiveFeatureGenerator}s.
 * <p>
 * <b>Note:</b><br>
 * All implementing classes must be thread safe.
 * 
 * @see AdaptiveFeatureGenerator
 * @see FeatureGeneratorResourceProvider
 * 
 * 
 * @deprecated do not use this interface, will be removed!
 */
@Deprecated
public interface FeatureGeneratorFactory {
  
  /**
   * Constructs a new {@link AdaptiveFeatureGenerator}.
   * <p>
   * <b>Note:</b><br>
   * It is assumed that all resource objects are thread safe and can be shared
   * between multiple instances of feature generators. If that is not the
   * case the implementor should make a copy of the resource object.
   * All resource objects that are included in OpenNLP can be assumed to be thread safe.
   * 
   * @param resourceProvider provides access to resources which are needed for feature generation.
   * 
   * @return the newly created feature generator
   */
  @Deprecated
  AdaptiveFeatureGenerator createFeatureGenerator(FeatureGeneratorResourceProvider resourceProvider);
}
