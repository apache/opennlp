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

package opennlp.tools.formats.muc;

import java.util.Set;

class MucElementNames {

  static final String DOC_ELEMENT = "DOC";
  static final String HEADLINE_ELEMENT = "HL";
  static final String DATELINE_ELEMENT = "DATELINE";
  static final String DD_ELEMENT = "DD";
  static final String SENTENCE_ELEMENT = "s";

  static final Set<String> CONTENT_ELEMENTS;

  static {
    CONTENT_ELEMENTS = Set.of(
            MucElementNames.HEADLINE_ELEMENT, MucElementNames.DATELINE_ELEMENT,
            MucElementNames.DD_ELEMENT, MucElementNames.SENTENCE_ELEMENT);
  }

  private MucElementNames() {
  }
}
