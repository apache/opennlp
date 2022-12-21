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


package opennlp.tools.postag;

import opennlp.tools.util.BeamSearchContextGenerator;


/**
 * Interface for a {@link BeamSearchContextGenerator} used in POS tagging.
 */
public interface POSContextGenerator extends BeamSearchContextGenerator<String> {

  /**
   * Returns the context for making a postag decision at the specified token {@code index}
   * given the specified {@code tokens} and previous {@code tags}.
   *
   * @param index The index of the token for which the context is provided.
   * @param tokens The token sequence representing a sentence.
   * @param prevTags The tags assigned to the previous words in the sentence.
   * @param additionalContext The context for additional information.
   *
   * @return The context for making a postag decision at the specified token {@code index}
   *     given the specified {@code tokens} and previous {@code tags}.
   */
  String[] getContext(int index, String[] tokens, String[] prevTags, Object[] additionalContext);
}
