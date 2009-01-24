/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

package opennlp.tools.parser;

import java.util.List;

import opennlp.tools.chunker.Chunker;
import opennlp.tools.util.Sequence;

/**
 * Interface that a chunker used with the parser should implement.
 */
public interface ParserChunker extends Chunker {
  /**
   * Returns the top k chunk sequences for the specified sentence with the specified pos-tags
   * @param sentence The tokens of the sentence.
   * @param tags The pos-tags for the specified sentence.
   * @return the top k chunk sequences for the specified sentence.
   */
  public Sequence[] topKSequences(List<String> sentence, List<String> tags);

  /**
   * Returns the top k chunk sequences for the specified sentence with the specified pos-tags
   * @param sentence The tokens of the sentence.
   * @param tags The pos-tags for the specified sentence.
   * @return the top k chunk sequences for the specified sentence.
   */
  public Sequence[] topKSequences(String[] sentence, String[] tags, double minSequenceScore);
}
