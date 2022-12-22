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

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Describes generic ways to read data from a {@link DataInputStream}.
 */
public interface DataReader {

  /**
   * @return Reads and returns a {@code double} value.
   * @throws IOException Thrown if IO errors occurred during read operation.
   */
  double readDouble() throws IOException;

  /**
   * @return Reads and returns an {@code int} value.
   * @throws IOException Thrown if IO errors occurred during read operation.
   */
  int readInt() throws IOException;

  /**
   * @return Reads and returns {@link String UTF-encoded characters}.
   * @throws IOException Thrown if IO errors occurred during read operation.
   */
  String readUTF() throws IOException;
}
