/*
 * Copyright 2013 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.tools.entitylinker;

import java.util.List;
import opennlp.tools.entitylinker.domain.LinkedSpan;
import opennlp.tools.util.Span;

/**
 * Structure for scoring linked entities. The Map logically represents a pair :
 * "Score type" to the "actual Score."
 */
public interface LinkedEntityScorer<T> {

/**
 * Scores a collection of linked entities. Implementations should populate the scoreMap in the list of BaseLink for each linkedSpan
 * @param linkedSpans the spans that have been linked to some external source and have all the data they need to be scored
 * @param docText the full text of the document.
 * @param sentenceSpans the sentence spans the correspond to the document text
 * @param additionalContext any additional data required to perform the scoring operation
 * @return void
 */
  void score(List<LinkedSpan> linkedSpans, String docText, Span[] sentenceSpans, T additionalContext);
}
