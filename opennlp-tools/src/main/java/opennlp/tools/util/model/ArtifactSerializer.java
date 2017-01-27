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
   * Creates the artifact from the provided {@link InputStream}.
   *
   * The {@link InputStream} remains open.
   *
   * @return the artifact
   *
   * @throws IOException
   */
  T create(InputStream in) throws IOException;

  /**
   * Serializes the artifact to the provided {@link OutputStream}.
   *
   * The {@link OutputStream} remains open.
   *
   * @param artifact
   * @param out
   * @throws IOException
   */
  void serialize(T artifact, OutputStream out) throws IOException;
}
