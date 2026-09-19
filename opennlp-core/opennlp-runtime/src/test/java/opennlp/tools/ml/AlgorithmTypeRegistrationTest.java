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

import java.io.ByteArrayInputStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import opennlp.tools.commons.Trainer;
import opennlp.tools.ml.model.AbstractModelReader;
import opennlp.tools.ml.model.BinaryFileDataReader;
import opennlp.tools.ml.model.DataReader;
import opennlp.tools.ml.model.ModelReaderFactory;
import opennlp.tools.ml.model.ModelWriterFactory;
import opennlp.tools.util.ext.ExtensionLoader;
import opennlp.tools.util.ext.ExtensionRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Every {@link AlgorithmType} names a trainer, a reader and a writer class. The
 * machine learning modules on the class path must have registered all three, or
 * GenericModelReader, GenericModelWriter and TrainerFactory cannot create them.
 */
public class AlgorithmTypeRegistrationTest {

  @ParameterizedTest
  @EnumSource(AlgorithmType.class)
  void testTrainerIsRegistered(AlgorithmType type) {
    Trainer<?> trainer = ExtensionLoader.instantiateExtension(Trainer.class, type.getTrainerClazz());
    assertEquals(type.getTrainerClazz(), trainer.getClass().getName());
  }

  @ParameterizedTest
  @EnumSource(AlgorithmType.class)
  void testReaderIsRegistered(AlgorithmType type) throws Exception {
    ModelReaderFactory factory =
        ExtensionRegistry.getDefault().factory(type.getReaderClazz(), ModelReaderFactory.class);
    assertNotNull(factory, type.getReaderClazz() + " must be registered");
    DataReader dataReader = new BinaryFileDataReader(new ByteArrayInputStream(new byte[0]));
    AbstractModelReader reader = factory.create(dataReader);
    assertEquals(type.getReaderClazz(), reader.getClass().getName());
  }

  @ParameterizedTest
  @EnumSource(AlgorithmType.class)
  void testWriterIsRegistered(AlgorithmType type) {
    ModelWriterFactory factory =
        ExtensionRegistry.getDefault().factory(type.getWriterClazz(), ModelWriterFactory.class);
    assertNotNull(factory, type.getWriterClazz() + " must be registered");
  }
}
