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

package opennlp.tools.postag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;

import opennlp.tools.util.model.ModelType;

public class POSModelTest {

  @Test
  public void testPOSModelSerializationMaxent() throws IOException {
    POSModel posModel = POSTaggerMETest.trainPOSModel(ModelType.MAXENT);

    ByteArrayOutputStream out = new ByteArrayOutputStream();

    try {
      posModel.serialize(out);
    }
    finally {
      out.close();
    }

    POSModel recreatedPosModel = new POSModel(new ByteArrayInputStream(out.toByteArray()));

    // TODO: add equals to pos model
  }

  @Test
  public void testPOSModelSerializationPerceptron() throws IOException {
    POSModel posModel = POSTaggerMETest.trainPOSModel(ModelType.PERCEPTRON);

    ByteArrayOutputStream out = new ByteArrayOutputStream();

    try {
      posModel.serialize(out);
    }
    finally {
      out.close();
    }

    POSModel recreatedPosModel = new POSModel(new ByteArrayInputStream(out.toByteArray()));

    // TODO: add equals to pos model
  }
}