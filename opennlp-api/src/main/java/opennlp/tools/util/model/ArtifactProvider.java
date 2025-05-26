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

package opennlp.tools.util.model;

/**
 * Provides access to model persisted artifacts.
 */
public interface ArtifactProvider {

  /**
   * @return Gets an artifact by an identifying key or name.
   */
  <T> T getArtifact(String key);

  /**
   * @param key A string identifying an element.
   *
   * @return Retrieves the value for the given {@code key} from the {@code manifest.properties}.
   */
  String getManifestProperty(String key);

  /**
   * @return Retrieves the language code of the material which was used to train a model
   *         or {@code 'x-unspecified'} if non was set.
   */
  String getLanguage();

  /**
   * Indicates if this provider was loaded from a serialized form.
   * <p>
   * It is useful, for example, during the validation of artifacts:
   * Skip the time-consuming ones if those were already validated during the
   * serialization process.
   *
   * @return {@code true} if this model was loaded from a serialized form, {@code false} otherwise.
   */
  boolean isLoadedFromSerialized();
}
