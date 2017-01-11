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

package opennlp.tools.namefind;

import opennlp.tools.util.Span;

/**
 * Name finding interface which processes an entire document allowing the name finder to use context
 * from the entire document.
 *
 * <strong>EXPERIMENTAL</strong>.
 * This interface has been added as part of a work in progress and might change without notice.
 */
public interface DocumentNameFinder {

  /**
   * Returns tokens span for the specified document of sentences and their tokens.
   * Span start and end indices are relative to the sentence they are in.
   * For example, a span identifying a name consisting of the first and second word
   * of the second sentence would be 0..2 and be referenced as spans[1][0].
   *
   * @param document An array of tokens for each sentence of a document.
   * @return The token spans for each sentence of the specified document.
   */
  Span[][] find(String[][] document);

}
