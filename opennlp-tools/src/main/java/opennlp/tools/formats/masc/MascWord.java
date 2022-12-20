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

public class MascWord extends Span {

  private static final long serialVersionUID = 2133473549058189775L;
  private final int id;

  /**
   * Holds one of MASC's quarks, that is: basic-level units (may be sub-word).
   *
   * @param s  The beginning of the word in the corpus file.
   *           Must be equal to or greater than {@code 0}.
   * @param e  The end of the word in the corpus file.
   *           Must be equal to or greater than {@code 0} and be greater than {@code s}.
   * @param id The id as assigned by the stand-off annotation.
   *           
   * @throws IllegalArgumentException Thrown if one of the parameters are invalid.
   */
  public MascWord(int s, int e, int id) {
    super(s, e);
    this.id = id;
  }

  public int getId() {
    return id;
  }

}
