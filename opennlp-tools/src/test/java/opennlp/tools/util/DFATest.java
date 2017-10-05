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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DFATest {

  private static final int[][] MOVE_FUNCTION = {
      { 0, 1, DFA.ERROR_STATE, DFA.ERROR_STATE, DFA.ERROR_STATE},
      { DFA.ERROR_STATE, 1, 2, DFA.ERROR_STATE, DFA.ERROR_STATE},
      { DFA.ERROR_STATE, DFA.ERROR_STATE, 2, 3, DFA.ERROR_STATE},
      { 0, 1, 2, 3, 3 }
  };
  private DFA dfa;

  @Before
  public void setUp() {
    dfa = new DFA(0, MOVE_FUNCTION, new int[]{ 2, 3 });
  }

  @Test
  public void testReadOneBuOne() throws Exception {
    // null isn't accepted
    Assert.assertFalse(dfa.accept());

    // 0* isn't accepted
    Assert.assertTrue(dfa.read(0));
    Assert.assertTrue(dfa.read(0));
    Assert.assertTrue(dfa.read(0));
    Assert.assertFalse(dfa.accept());

    // 0* 1* isn't accepted
    dfa.reset();
    Assert.assertTrue(dfa.read(0));
    Assert.assertTrue(dfa.read(0));
    Assert.assertTrue(dfa.read(1));
    Assert.assertTrue(dfa.read(1));
    Assert.assertTrue(dfa.read(1));
    Assert.assertFalse(dfa.accept());

    // 0* 1* 2* is accepted
    dfa.reset();
    Assert.assertTrue(dfa.read(0));
    Assert.assertTrue(dfa.read(0));
    Assert.assertTrue(dfa.read(1));
    Assert.assertTrue(dfa.read(1));
    Assert.assertTrue(dfa.read(1));
    Assert.assertTrue(dfa.read(2));
    Assert.assertTrue(dfa.read(2));
    Assert.assertTrue(dfa.accept());
  }

  @Test
  public void testReadSeqSymbols() throws Exception {
    // 0* isn't accepted
    Assert.assertFalse(dfa.read(new int[] {0, 0, 0}));

    // 0* 1* isn't accepted
    dfa.reset();
    Assert.assertFalse(dfa.read(new int[] {0, 0, 0, 1, 1}));

    // 0* 1* 2* is accepted
    dfa.reset();
    Assert.assertTrue(dfa.read(new int[] {0, 0, 0, 1, 1, 2, 2, 2}));

    // 0* 1* 2* 3* is accepted
    dfa.reset();
    Assert.assertTrue(dfa.read(new int[] {0, 0, 0, 1, 1, 2, 2, 2, 3, 3, 3}));

    // 0* 1* 2* 3* 1* isn't accepted
    dfa.reset();
    Assert.assertFalse(dfa.read(new int[] {0, 0, 0, 1, 1, 2, 2, 2, 3, 3, 1}));

    // 0* 1* 2* 3* 4 is accepted
    dfa.reset();
    Assert.assertTrue(dfa.read(new int[] {0, 0, 0, 1, 1, 2, 2, 2, 3, 3, 4}));

    // 0* 1* 2* 3* 4 2* is accepted
    dfa.reset();
    Assert.assertTrue(dfa.read(new int[] {0, 0, 0, 1, 1, 2, 2, 2, 3, 3, 4, 2, 2}));
  }
}
