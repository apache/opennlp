/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl;

/**
 * Holds the tokens for input to an ONNX model.
 */
public class Tokens {

  private final String[] tokens;
  private final long[] ids;
  private final long[] mask;
  private final long[] types;

  /**
   * Creates a new instance to hold the tokens for input to an ONNX model.
   * @param tokens The tokens themselves.
   * @param ids The token IDs as retrieved from the vocabulary.
   * @param mask The token mask. (Typically all 1.)
   * @param types The token types. (Typically all 1.)
   */
  public Tokens(String[] tokens, long[] ids, long[] mask, long[] types) {

    this.tokens = tokens;
    this.ids = ids;
    this.mask = mask;
    this.types = types;

  }

  public String[] getTokens() {
    return tokens;
  }

  public long[] getIds() {
    return ids;
  }

  public long[] getMask() {
    return mask;
  }

  public long[] getTypes() {
    return types;
  }

}
