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
package opennlp.tools.util.normalizer;

import java.util.Random;

/**
 * Input construction shared by the characterization tests of the de-regexed normalizers, so the
 * generation and failure-reporting logic cannot drift apart between the four suites.
 */
final class CharacterizationInputs {

  private CharacterizationInputs() {
  }

  static String randomInput(Random random, String[] pool) {
    final int pieces = random.nextInt(24);
    final StringBuilder b = new StringBuilder();
    for (int i = 0; i < pieces; i++) {
      b.append(pool[random.nextInt(pool.length)]);
    }
    return b.toString();
  }

  static String escape(String s) {
    final StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      if (c >= 0x20 && c <= 0x7E) {
        b.append(c);
      } else {
        b.append(String.format("\\u%04X", (int) c));
      }
    }
    return b.toString();
  }
}
