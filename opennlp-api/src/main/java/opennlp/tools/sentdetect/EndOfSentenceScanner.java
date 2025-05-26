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

package opennlp.tools.sentdetect;

import java.util.List;
import java.util.Set;

/**
 * Scans {@link CharSequence}, {@link StringBuffer}, and {@code char[]} for the offsets of
 * sentence ending characters.
 *
 * <p>Implementations of this interface can use regular expressions,
 * hand-coded DFAs, and other scanning techniques to locate end of
 * sentence offsets.</p>
 */

public interface EndOfSentenceScanner {

  /**
   * @return a set of {@link Character characters} which can indicate the end of a sentence.
   */
  Set<Character> getEOSCharacters();

  /**
   * The receiver scans the specified string for sentence ending characters and
   * returns their offsets.
   *
   * @param s A {@link CharSequence} to be scanned.
   * @return A {@link List} of Integer objects.
   */
  List<Integer> getPositions(CharSequence s);

  /**
   * The receiver scans {@code buf} for sentence ending characters and
   * returns their offsets.
   *
   * @param buf A {@link StringBuffer} to be scanned.
   * @return A {@link List} of Integer objects.
   */
  List<Integer> getPositions(StringBuffer buf);

  /**
   * The receiver scans {@code cbuf} for sentence ending characters and
   * returns their offsets.
   *
   * @param cbuf A {@code char[]} to be scanned.
   * @return A {@link List} of Integer objects.
   */
  List<Integer> getPositions(char[] cbuf);
}
