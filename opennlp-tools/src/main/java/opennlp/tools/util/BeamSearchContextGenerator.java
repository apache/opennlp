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
 * Interface for context generators used with a sequence beam search.
 */
public interface BeamSearchContextGenerator<T> {

  /** Returns the context for the specified position in the specified sequence (list).
     * @param index The index of the sequence.
     * @param sequence  The sequence of items over which the beam search is performed.
     * @param priorDecisions The sequence of decisions made prior to the context for
     *     which this decision is being made.
     * @param additionalContext Any addition context specific to a class implementing this interface.
     * @return the context for the specified position in the specified sequence.
     */
  String[] getContext(int index, T[] sequence, String[] priorDecisions, Object[] additionalContext);
}
