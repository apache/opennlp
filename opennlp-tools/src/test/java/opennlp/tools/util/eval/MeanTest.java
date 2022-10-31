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

package opennlp.tools.util.eval;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the {@link Mean} class.
 */
public class MeanTest {

  @Test
  public void testMeanCalculation() {
    Mean a = new Mean();
    a.add(1);
    Assert.assertEquals(1, a.count());
    Assert.assertEquals(1d, a.mean(), 0.00001d);

    a.add(1);
    Assert.assertEquals(2, a.count());
    Assert.assertEquals(1d, a.mean(), 0.00001d);
    a.toString();

    Mean b = new Mean();
    b.add(0.5);
    Assert.assertEquals(1, b.count());
    Assert.assertEquals(0.5d, b.mean(), 0.00001d);

    b.add(2);
    Assert.assertEquals(2, b.count());
    Assert.assertEquals(1.25d, b.mean(), 0.00001d);
    b.toString();

    Mean c = new Mean();
    Assert.assertEquals(0, c.count());
    Assert.assertEquals(0d, c.mean(), 0.00001d);
    c.toString();
  }

}
