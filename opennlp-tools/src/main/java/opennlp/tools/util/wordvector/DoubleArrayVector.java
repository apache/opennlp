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

class DoubleArrayVector implements WordVector {

  private double[] vector;

  DoubleArrayVector(double[] vector) {
    this.vector = vector;
  }

  @Override
  public WordVectorType getDataType() {
    return WordVectorType.DOUBLE;
  }

  @Override
  public float getAsFloat(int index) {
    return (float) getAsDouble(index);
  }

  @Override
  public double getAsDouble(int index) {
    return vector[index];
  }

  @Override
  public float[] toFloatArray() {
    float[] floatVector = new float[vector.length];
    for (int i = 0; i < floatVector.length ; i++) {
      floatVector[i] = (float) vector[i];
    }
    return floatVector;
  }

  @Override
  public double[] toDoubleArray() {
    return toDoubleBuffer().array();
  }

  @Override
  public FloatBuffer toFloatBuffer() {
    return FloatBuffer.wrap(toFloatArray()).asReadOnlyBuffer();
  }

  @Override
  public DoubleBuffer toDoubleBuffer() {
    return DoubleBuffer.wrap(vector);
  }

  @Override
  public int dimension() {
    return vector.length;
  }
}
