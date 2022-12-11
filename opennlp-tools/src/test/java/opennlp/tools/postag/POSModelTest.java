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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.model.ModelType;

public class POSModelTest {

  @Test
  void testPOSModelSerializationMaxent() throws IOException {
    POSModel posModel = POSTaggerMETest.trainPOSModel(ModelType.MAXENT);
    Assertions.assertFalse(posModel.isLoadedFromSerialized());

    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      posModel.serialize(out);

      POSModel recreatedPosModel = new POSModel(new ByteArrayInputStream(out.toByteArray()));
      Assertions.assertNotNull(recreatedPosModel);
      Assertions.assertTrue(recreatedPosModel.isLoadedFromSerialized());
      Assertions.assertEquals(posModel, recreatedPosModel);
    }
  }

  @Test
  void testPOSModelSerializationPerceptron() throws IOException {
    POSModel posModel = POSTaggerMETest.trainPOSModel(ModelType.PERCEPTRON);
    Assertions.assertFalse(posModel.isLoadedFromSerialized());
    
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      posModel.serialize(out);

      POSModel recreatedPosModel = new POSModel(new ByteArrayInputStream(out.toByteArray()));
      Assertions.assertTrue(recreatedPosModel.isLoadedFromSerialized());
      Assertions.assertEquals(posModel, recreatedPosModel);
    }

  }
}
