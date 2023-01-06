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

package opennlp.tools.ml.model;

import opennlp.tools.util.BeamSearchContextGenerator;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.SequenceValidator;

/**
 * A classification model that can label an input {@link Sequence}.
 *
 * @param <T> The type of the object which is the source.
 */
public interface SequenceClassificationModel<T> {

  /**
   * Finds the {@link Sequence} with the highest probability.
   *
   * @param sequence The {@link T sequence} used as input.
   * @param additionalContext An array that provides additional information (context).
   * @param cg The {@link BeamSearchContextGenerator} to use.
   * @param validator The {@link SequenceValidator} to validate with.
   *
   * @return The {@link Sequence} with the highest probability.
   */
  Sequence bestSequence(T[] sequence, Object[] additionalContext,
      BeamSearchContextGenerator<T> cg, SequenceValidator<T> validator);

  /**
   * Finds the n most probable {@link Sequence sequences} with the highest probability.
   *
   * @param numSequences The number of sequences to compute.
   * @param sequence The {@link T sequence} used as input.
   * @param additionalContext An array that provides additional information (context).
   * @param minSequenceScore The minimum score to achieve.
   * @param cg The {@link BeamSearchContextGenerator} to use.
   * @param validator The {@link SequenceValidator} to validate with.
   *
   * @return The {@link Sequence sequences} with the highest probability.
   */
  Sequence[] bestSequences(int numSequences, T[] sequence, Object[] additionalContext,
      double minSequenceScore, BeamSearchContextGenerator<T> cg, SequenceValidator<T> validator);

  /**
   * Finds the n most probable {@link Sequence sequences} with the highest probability.
   *
   * @param numSequences The number of sequences to compute.
   * @param sequence The {@link T sequence} used as input.
   * @param additionalContext An array that provides additional information (context).
   * @param cg The {@link BeamSearchContextGenerator} to use.
   * @param validator The {@link SequenceValidator} to validate with.
   *
   * @return The {@link Sequence sequences} with the highest probability.
   */
  Sequence[] bestSequences(int numSequences, T[] sequence,
      Object[] additionalContext, BeamSearchContextGenerator<T> cg, SequenceValidator<T> validator);

  /**
   * @return Retrieves all possible outcomes.
   */
  String[] getOutcomes();
}
