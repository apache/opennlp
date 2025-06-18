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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link DefaultEndOfSentenceScanner} class.
 */
public class DefaultEndOfSentenceScannerTest {

  @Test
  void testScanning() {
    EndOfSentenceScanner scanner = new DefaultEndOfSentenceScanner(
        new char[] {'.', '!', '?'});

    List<Integer> eosPositions =
        scanner.getPositions("... um die Wertmarken zu auswählen !?");

    Assertions.assertEquals(0, eosPositions.get(0).intValue());
    Assertions.assertEquals(1, eosPositions.get(1).intValue());
    Assertions.assertEquals(2, eosPositions.get(2).intValue());

    Assertions.assertEquals(35, eosPositions.get(3).intValue());
    Assertions.assertEquals(36, eosPositions.get(4).intValue());
  }

}
