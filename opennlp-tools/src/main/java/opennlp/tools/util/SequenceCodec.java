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

import java.util.List;

/**
 * A codec for sequences of type {@link T}.
 * Defines methods to en- or decode, and validate.
 *
 * @param <T> The generic type for the elements to handle.
 */
public interface SequenceCodec<T> {

  /**
   * Decodes a sequence of {@link T objects} into {@link Span} objects.
   *
   * @param c A list of {@link T} to decode.
   *
   * @return A {@link Span} array encapsulating the decoded elements in {@code c}.
   */
  Span[] decode(List<T> c);

  /**
   * Encodes {@link Span} objects into a sequence of {@link T objects}.
   *
   * @param names A list of {@link Span elements} to encode.
   * @param length The length to respect.
   *
   * @return An array of {@link T} to encode.
   */
  T[] encode(Span[] names, int length);

  /**
   * @return A {@link SequenceValidator} which can validate a sequence of {@link T outcomes}.
   */
  SequenceValidator<T> createSequenceValidator();

  /**
   * Checks if the {@code outcomes} of a model are compatible with this {@link SequenceCodec}.
   *
   * @param outcomes The possible model outcomes.
   *
   * @return {@code true} if {@code outcomes} are type compatible, {@code false} otherwise.
   */
  boolean areOutcomesCompatible(String[] outcomes);
}
