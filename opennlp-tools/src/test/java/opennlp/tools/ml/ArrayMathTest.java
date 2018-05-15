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

package opennlp.tools.ml;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

public class ArrayMathTest {

  @Test
  public void testInnerProductDoubleNaN() throws Exception {
    Assert.assertTrue(Double.isNaN(ArrayMath.innerProduct(null, new double[]{0})));
    Assert.assertTrue(Double.isNaN(ArrayMath.innerProduct(new double[]{0}, null)));
    Assert.assertTrue(Double.isNaN(ArrayMath.innerProduct(new double[]{0, 1, 2}, new double[]{0, 1, 2, 3})));
  }

  @Test
  public void testInnerProduct() throws Exception {
    Assert.assertEquals(0, ArrayMath.innerProduct(new double[] {}, new double[] {}), 0);
    Assert.assertEquals(-1, ArrayMath.innerProduct(new double[] {1}, new double[] {-1}), 0);
    Assert.assertEquals(14, ArrayMath.innerProduct(new double[] {1, 2, 3}, new double[] {1, 2, 3}), 0);
  }

  @Test
  public void testL1Norm() throws Exception {
    Assert.assertEquals(0, ArrayMath.l1norm(new double[]{}), 0);
    Assert.assertEquals(0, ArrayMath.l1norm(new double[] {0}), 0);
    Assert.assertEquals(2, ArrayMath.l1norm(new double[] {1, -1}), 0);
    Assert.assertEquals(55, ArrayMath.l1norm(new double[] {1, -2, 3, -4, 5, -6, 7, -8, 9, -10}), 0);
  }

  @Test
  public void testL2Norm() throws Exception {
    Assert.assertEquals(0, ArrayMath.l2norm(new double[] {}), 0);
    Assert.assertEquals(0, ArrayMath.l2norm(new double[] {0}), 0);
    Assert.assertEquals(1.41421, ArrayMath.l2norm(new double[] {1, -1}), 0.001);
    Assert.assertEquals(0.54772, ArrayMath.l2norm(new double[] {0.1, -0.2, 0.3, -0.4}), 0.001);
  }

  @Test
  public void testInvL2Norm() throws Exception {
    Assert.assertEquals(0.70711, ArrayMath.invL2norm(new double[] {1, -1}), 0.001);
    Assert.assertEquals(1.82575, ArrayMath.invL2norm(new double[] {0.1, -0.2, 0.3, -0.4}), 0.001);
  }

  @Test
  public void testLogSumOfExps() throws Exception {
    Assert.assertEquals(0, ArrayMath.logSumOfExps(new double[] {0}), 0);
    Assert.assertEquals(1, ArrayMath.logSumOfExps(new double[] {1}), 0);
    Assert.assertEquals(2.048587, ArrayMath.logSumOfExps(new double[] {-1, 2}), 0.001);
    Assert.assertEquals(1.472216, ArrayMath.logSumOfExps(new double[] {-0.1, 0.2, -0.3, 0.4}), 0.001);
  }

  @Test
  public void testMax() throws Exception {
    Assert.assertEquals(0, ArrayMath.max(new double[] {0}), 0);
    Assert.assertEquals(0, ArrayMath.max(new double[] {0, 0, 0}), 0);
    Assert.assertEquals(2, ArrayMath.max(new double[] {0, 1, 2}), 0);
    Assert.assertEquals(200, ArrayMath.max(new double[] {100, 200, 2}), 0);
    Assert.assertEquals(300, ArrayMath.max(new double[] {100, 200, 300, -10, -20}), 0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testArgmaxException1() throws Exception {
    ArrayMath.argmax(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testArgmaxException2() throws Exception {
    ArrayMath.argmax(new double[]{});
  }

  @Test
  public void testArgmax() throws Exception {
    Assert.assertEquals(0, ArrayMath.argmax(new double[] {0}));
    Assert.assertEquals(0, ArrayMath.argmax(new double[] {0, 0, 0}));
    Assert.assertEquals(2, ArrayMath.argmax(new double[] {0, 1, 2}));
    Assert.assertEquals(1, ArrayMath.argmax(new double[] {100, 200, 2}));
    Assert.assertEquals(2, ArrayMath.argmax(new double[] {100, 200, 300, -10, -20}));
  }

  @Test
  public void testToDoubleArray() throws Exception {
    Assert.assertEquals(0, ArrayMath.toDoubleArray(Collections.EMPTY_LIST).length);
    Assert.assertArrayEquals(new double[] {0}, ArrayMath.toDoubleArray(Arrays.asList(0D)), 0);
    Assert.assertArrayEquals(new double[] {0, 1, -2.5, -0.3, 4},
        ArrayMath.toDoubleArray(Arrays.asList(0D, 1D, -2.5D, -0.3D, 4D)), 0);
  }

  @Test
  public void testToIntArray() throws Exception {
    Assert.assertEquals(0, ArrayMath.toIntArray(Collections.EMPTY_LIST).length);
    Assert.assertArrayEquals(new int[] {0}, ArrayMath.toIntArray(Arrays.asList(0)));
    Assert.assertArrayEquals(new int[] {0, 1, -2, -3, 4},
        ArrayMath.toIntArray(Arrays.asList(0, 1, -2, -3, 4)));
  }
}
