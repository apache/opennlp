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

package opennlp.tools.formats.masc;

import opennlp.tools.util.Span;

/**
 * A specialized {@link Span} to express tokens in {@link MascDocument documents}.
 */
public class MascToken extends Span {

  private static final long serialVersionUID = -780646706788037041L;
  private final String pos;
  private final String base;
  private final int tokenId;
  private final MascWord[] quarks;

  /**
   * Initializes a {@link MascToken} which may combine multiple quarks.
   *
   * @param s      The start of the token in the corpus file.
   *               Must be equal to or greater than {@code 0}.
   * @param e      The end of the token in the corpus file.
   *               Must be equal to or greater than {@code 0} and be greater than {@code s}.
   * @param pennId The ID of the token as assigned by the Penn stand-off annotation.
   * @param pos    The POS-tag.
   * @param base   The base form.
   * @param quarks The {@link MascWord array of Quarks} contained in the token.
   *
   * @throws IllegalArgumentException Thrown if one of the parameters are invalid.
   */
  public MascToken(int s, int e, int pennId, String pos, String base, MascWord[] quarks) {
    super(s, e);
    this.pos = pos;
    this.base = base;
    this.tokenId = pennId;
    this.quarks = quarks;
  }

  /**
   * @return Retrieves the ID of the token.
   */
  public int getTokenId() {
    return tokenId;
  }

  /**
   * @return Retrieves the base form.
   */
  public String getBase() {
    return base;
  }

  /**
   * @return Retrieves the POS tag.
   */
  public String getPos() {
    return pos;
  }

  /**
   * @return Retrieves quarks of the token.
   */
  public MascWord[] getQuarks() {
    return quarks;
  }

}
