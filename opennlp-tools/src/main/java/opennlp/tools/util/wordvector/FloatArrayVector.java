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

class FloatArrayVector implements WordVector {

  private float[] vector;

  FloatArrayVector(float[] vector) {
    this.vector = vector;
  }

  @Override
  public WordVectorType getDataType() {
    return WordVectorType.FLOAT;
  }

  @Override
  public float getAsFloat(int index) {
    return vector[index];
  }

  @Override
  public double getAsDouble(int index) {
    return getAsFloat(index);
  }

  @Override
  public FloatBuffer toFloatBuffer() {
    return FloatBuffer.wrap(vector).asReadOnlyBuffer();
  }

  @Override
  public DoubleBuffer toDoubleBuffer() {
    double[] doubleVector = new double[vector.length];
    for (int i = 0; i < doubleVector.length ; i++) {
      doubleVector[i] = vector[i];
    }
    return DoubleBuffer.wrap(doubleVector).asReadOnlyBuffer();
  }

  @Override
  public int dimension() {
    return vector.length;
  }
}
