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

package opennlp.tools.ml.model;

import org.junit.Assert;
import org.junit.Test;

public class EventTest {

  @Test
  public void testNullOutcome() {
    try {
      new Event(null, new String[]{"aa", "bb", "cc"});
      Assert.fail("NPE must be thrown");
    }
    catch (NullPointerException expected) {
    }
  }

  @Test
  public void testNullContext() {
    try {
      new Event("o1", null);
      Assert.fail("NPE must be thrown");
    }
    catch (NullPointerException expected) {
    }
  }

  @Test
  public void testWithValues() {
    Event event = new Event("o1",
            new String[]{"aa", "bb", "cc"});

    Assert.assertEquals("o1", event.getOutcome());
    Assert.assertArrayEquals(new String[]{"aa", "bb", "cc"}, event.getContext());
    Assert.assertNull(event.getValues());
    Assert.assertEquals("o1 [aa bb cc]", event.toString());
  }

  @Test
  public void testWithoutValues() {
    Event event = new Event("o1",
            new String[]{"aa", "bb", "cc"},
            new float[]{0.2F, 0.4F, 0.4F});

    Assert.assertEquals("o1", event.getOutcome());
    Assert.assertArrayEquals(new String[]{"aa", "bb", "cc"}, event.getContext());
    Assert.assertArrayEquals(new float[]{0.2F, 0.4F, 0.4F}, event.getValues(), 0.001F);
    Assert.assertEquals("o1 [aa=0.2 bb=0.4 cc=0.4]", event.toString());
  }
}
