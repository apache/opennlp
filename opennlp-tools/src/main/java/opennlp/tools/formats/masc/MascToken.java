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

public class MascToken extends Span {

  private final String pos;
  private final String base;
  private final int tokenId;
  private final MascWord[] quarks;

  /**
   * Create a MascToken, which may combine multiple quarks
   *
   * @param s      The start of the token in the corpus file
   * @param e      The end of the token in the corpus file
   * @param pennId The ID of the token as assigned by the Penn stand-off annotation
   * @param pos    The POS-tag
   * @param base   The base form
   * @param quarks Quarks contained in the token
   */
  public MascToken(int s, int e, int pennId, String pos, String base, MascWord[] quarks) {
    super(s, e);
    this.pos = pos;
    this.base = base;
    this.tokenId = pennId;
    this.quarks = quarks;
  }

  /**
   * Get ID of the token
   *
   * @return the ID
   */
  public int getTokenId() {
    return tokenId;
  }

  /**
   * Get the base form
   *
   * @return the base form
   */
  public String getBase() {
    return base;
  }

  /**
   * Get the POS tag
   *
   * @return POS tag
   */
  public String getPos() {
    return pos;
  }

  /**
   * Get quarks of the token
   *
   * @return Array of quark references
   */
  public MascWord[] getQuarks() {
    return quarks;
  }

}
