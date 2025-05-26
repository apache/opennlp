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

import opennlp.tools.util.Span;

/**
 * The interface for sentence detectors, which find the sentence boundaries in
 * a text.
 */
public interface SentenceDetector {

  /**
   * Detects sentences in a character sequence.
   *
   * @param s The {@link CharSequence} for which sentences shall to be detected.
   * @return  The String[] with the individual sentences as the array
   *          elements.
   */
  String[] sentDetect(CharSequence s);

  /**
   * Detects sentences in a character sequence.
   *
   * @param s The {@link CharSequence} for which sentences shall be detected.
   *
   * @return The array of {@link Span spans} (offsets into {@code s}) for each
   * detected sentence as the individuals array elements.
   */
  Span[] sentPosDetect(CharSequence s);
}
