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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Responsible to create an artifact from an {@link InputStream}.
 */
public interface ArtifactSerializer<T> {

  /**
   * Creates an artifact from the provided {@link InputStream}.
   * <p>
   * <b>Note: The {@link InputStream} remains open.</b>
   *
   * @param in A valid, open {@link InputStream} ready to read from.
   * @return A valid {@link T artifact}.
   *
   * @throws IOException Thrown if IO errors occurred during creation.
   */
  T create(InputStream in) throws IOException;

  /**
   * Serializes an artifact to the provided {@link OutputStream}.
   * <p>
   * <b>Note: The {@link OutputStream} remains open.</b>
   *
   * @param artifact A valid {@link T artifact}.
   * @param out A valid, open {@link OutputStream} ready to write to.
   *            
   * @throws IOException Thrown if IO errors occurred during serialization.
   */
  void serialize(T artifact, OutputStream out) throws IOException;
}
