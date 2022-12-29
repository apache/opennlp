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
 * A marker interface so that implementing classes can refer to
 * the corresponding {@link ArtifactSerializer} implementation.
 */
public interface SerializableArtifact {

  /**
   * Retrieves the class which can serialize and recreate this artifact.
   * <p>
   * <b>Note:</b>
   * The serializer class must have a {@code public zero argument constructor}
   * or an exception is thrown during model serialization/loading.
   *
   * @return The corresponding {@link ArtifactSerializer} class.
   */
  Class<?> getArtifactSerializerClass();
}
