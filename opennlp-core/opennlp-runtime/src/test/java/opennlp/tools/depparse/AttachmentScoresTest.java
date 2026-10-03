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

package opennlp.tools.depparse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tests the accumulation and merging of {@link AttachmentScores}. */
public class AttachmentScoresTest {

  @Test
  void testAddsTokens() {
    final AttachmentScores scores = new AttachmentScores();
    scores.add(true, true, false);
    scores.add(true, false, false);
    scores.add(false, false, true);
    assertEquals(3, scores.getWordCount());
    assertEquals(2.0d / 3.0d, scores.getUas(), 1e-12);
    assertEquals(1.0d / 3.0d, scores.getLas(), 1e-12);
    assertEquals(2, scores.getWordCountExcludingPunctuation());
    assertEquals(1.0d, scores.getUasExcludingPunctuation());
    assertEquals(0.5d, scores.getLasExcludingPunctuation());
  }

  @Test
  void testMergeWeightsByTokenCount() {
    final AttachmentScores small = new AttachmentScores();
    small.add(false, false, false);
    final AttachmentScores large = new AttachmentScores();
    large.add(true, true, false);
    large.add(true, true, false);
    large.add(true, true, true);

    final AttachmentScores total = new AttachmentScores();
    total.add(small);
    total.add(large);
    assertEquals(4, total.getWordCount());
    assertEquals(0.75d, total.getUas());
    assertEquals(0.75d, total.getLas());
    assertEquals(3, total.getWordCountExcludingPunctuation());
    assertEquals(2.0d / 3.0d, total.getUasExcludingPunctuation(), 1e-12);
  }

  @Test
  void testMergingAnEmptyAccumulatorChangesNothing() {
    final AttachmentScores total = new AttachmentScores();
    total.add(true, false, false);
    total.add(new AttachmentScores());
    assertEquals(1, total.getWordCount());
    assertEquals(1.0d, total.getUas());
    assertEquals(0.0d, total.getLas());
  }

  @Test
  void testEmptyScoresAreZero() {
    final AttachmentScores scores = new AttachmentScores();
    assertEquals(0, scores.getWordCount());
    assertEquals(0.0d, scores.getUas());
    assertEquals(0.0d, scores.getLasExcludingPunctuation());
  }
}
