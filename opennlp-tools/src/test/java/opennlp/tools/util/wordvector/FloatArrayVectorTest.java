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

package opennlp.tools.util.wordvector;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FloatArrayVectorTest {

  private float[] floatArray;

  @BeforeEach
  public void setup() {
    floatArray = new float[]{Float.MIN_VALUE, -1, 0, 1, Float.MAX_VALUE};
  }

  @AfterEach
  public void tearDown() {
    floatArray = null;
  }

  @Test
  public void testGetDataType() {
    FloatArrayVector faVector = new FloatArrayVector(floatArray);
    Assertions.assertEquals(WordVectorType.FLOAT, faVector.getDataType());
  }

  @Test
  public void testGetDimension() {
    FloatArrayVector faVector = new FloatArrayVector(floatArray);
    Assertions.assertEquals(floatArray.length, faVector.dimension());
  }

  @Test
  public void testGetAsFloat() {
    FloatArrayVector faVector = new FloatArrayVector(floatArray);
    for (int i = 0; i < faVector.dimension(); i++) {
      Assertions.assertEquals(floatArray[i], faVector.getAsFloat(i));
    }
  }

  @Test
  public void testGetAsDouble() {
    FloatArrayVector faVector = new FloatArrayVector(floatArray);
    for (int i = 0; i < faVector.dimension(); i++) {
      Assertions.assertEquals(floatArray[i], faVector.getAsDouble(i));
    }
  }

  @Test
  public void testToFloatBuffer() {
    // reference
    FloatBuffer refBuffer = FloatBuffer.wrap(floatArray);

    FloatArrayVector faVector = new FloatArrayVector(floatArray);
    FloatBuffer fBuffer = faVector.toFloatBuffer();
    Assertions.assertNotNull(fBuffer);
    Assertions.assertEquals(0, refBuffer.compareTo(fBuffer));
  }

  @Test
  public void testToDoubleBuffer() {
    // reference
    final double[] doubleArray = new double[]{Float.MIN_VALUE, -1, 0, 1, Float.MAX_VALUE};
    DoubleBuffer refBuffer = DoubleBuffer.wrap(doubleArray);

    FloatArrayVector faVector = new FloatArrayVector(floatArray);
    DoubleBuffer dBuffer = faVector.toDoubleBuffer();
    Assertions.assertNotNull(dBuffer);
    Assertions.assertEquals(0, refBuffer.compareTo(dBuffer));
  }
}
