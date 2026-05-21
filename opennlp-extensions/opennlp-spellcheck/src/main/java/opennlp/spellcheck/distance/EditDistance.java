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

package opennlp.spellcheck.distance;

/**
 * Computes the edit distance between two character sequences with an upper bound.
 *
 * <p>Implementations must be Unicode-aware and operate on code points rather than
 * UTF-16 {@code char} units so that supplementary characters are handled correctly.</p>
 */
public interface EditDistance {

  /**
   * Computes the edit distance between {@code a} and {@code b}, giving up early once it
   * is certain the distance exceeds {@code max}.
   *
   * @param a   the first sequence; must not be {@code null}
   * @param b   the second sequence; must not be {@code null}
   * @param max the maximum acceptable distance; must not be negative
   * @return the edit distance, or {@code -1} if it is strictly greater than {@code max}
   */
  int distance(CharSequence a, CharSequence b, int max);
}
