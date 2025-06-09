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

package opennlp.tools.util;

/**
 * @param <T> The generic type that is to be validated.
 */
public interface SequenceValidator<T> {

  /**
   * Determines whether a particular continuation of a {@link T sequence} is valid.
   * This is used to restrict invalid sequences such as those used in start/continue tag-based chunking
   * or could be used to implement tag dictionary restrictions.
   *
   * @param i The index in the {@code inputSequence} for which the new outcome is being proposed.
   * @param inputSequence The input sequence of {@link T}.
   * @param outcomesSequence The outcomes so far in this sequence.
   * @param outcome The next proposed outcome for the outcomes sequence.
   *
   * @return {@code true} if the sequence would still be valid with the new outcome,
   *         {@code false} otherwise.
   */
  boolean validSequence(int i, T[] inputSequence, String[] outcomesSequence,
      String outcome);
}
