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
import org.junit.Test;

public class ListHeapTest {

  @Test
  public void testSimple() {
    int size = 5;
    Heap<Integer> heap = new ListHeap<>(size);

    for (int ai = 0; ai < 10; ai++) {
      if (ai < size) {
        Assert.assertEquals(ai, heap.size());
      } else {
        Assert.assertEquals(size, heap.size());
      }
      heap.add(ai);
    }

    Assert.assertEquals(Integer.valueOf(0), heap.extract());
    Assert.assertEquals(4, heap.size());

    Assert.assertEquals(Integer.valueOf(1), heap.extract());
    Assert.assertEquals(3, heap.size());

    Assert.assertEquals(Integer.valueOf(2), heap.extract());
    Assert.assertEquals(2, heap.size());

    Assert.assertEquals(Integer.valueOf(3), heap.extract());
    Assert.assertEquals(1, heap.size());

    Assert.assertEquals(Integer.valueOf(4), heap.extract());
    Assert.assertEquals(0, heap.size());

  }
}
