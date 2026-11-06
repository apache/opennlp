/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.tokenize;

/**
 * Checks whether a candidate token is accepted by the alphanumeric optimization of
 * {@link TokenizerME} and {@link TokSpanEventStream}, so it is not split further.
 */
@FunctionalInterface
interface AlphanumericCheck {

  /**
   * {@return {@code true} if {@code candidate} is accepted as alphanumeric}
   *
   * @param candidate The candidate token.
   */
  boolean check(CharSequence candidate);
}
