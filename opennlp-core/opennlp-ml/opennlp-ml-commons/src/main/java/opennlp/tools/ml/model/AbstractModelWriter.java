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

package opennlp.tools.ml.model;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * An abstract, basic implementation of a model writer.
 */
public abstract class AbstractModelWriter {

  public AbstractModelWriter() {
    super();
  }

  /**
   * Writes a {@link String} to the underlying {@link DataOutputStream}.
   *
   * @param s The {@link String UTF encoded} characters.
   * @throws IOException Thrown if IO errors occurred.
   */
  public abstract void writeUTF(String s) throws IOException;

  /**
   * Writes a single {@code int} to the underlying {@link DataOutputStream}.
   *
   * @param i The {@code int} value.
   * @throws IOException Thrown if IO errors occurred.
   */
  public abstract void writeInt(int i) throws IOException;

  /**
   * Writes a single {@code double} to the underlying {@link DataOutputStream}.
   *
   * @param d The {@code double} value.
   * @throws IOException Thrown if IO errors occurred.
   */
  public abstract void writeDouble(double d) throws IOException;

  /**
   * Closes the underlying {@link DataOutputStream}.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public abstract void close() throws IOException;

  /**
   * Serializes the {@link AbstractModel model} using the
   * {@link #writeUTF(String)}, {@link #writeDouble(double)},
   * or {@link #writeInt(int)}} methods implemented by
   * extending classes.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public abstract void persist() throws IOException;

}
