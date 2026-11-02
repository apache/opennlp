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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import opennlp.tools.ml.AlgorithmType;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.ext.ExtensionLoader;

public class GenericModelReaderWriterTest {

  private static final List<Event> EVENTS = List.of(
      new Event("yes", new String[] {"f=a", "g=x"}),
      new Event("yes", new String[] {"f=a", "g=y"}),
      new Event("yes", new String[] {"f=a", "g=x"}),
      new Event("no", new String[] {"f=b", "g=y"}),
      new Event("no", new String[] {"f=b", "g=x"}),
      new Event("no", new String[] {"f=b", "g=y"}));

  @ParameterizedTest
  @EnumSource(AlgorithmType.class)
  void testReaderAndWriterClassesResolve(AlgorithmType type) throws ClassNotFoundException {
    Assertions.assertNotNull(
        ExtensionLoader.loadExtensionClass(AbstractModelReader.class, type.getReaderClazz()));
    Assertions.assertNotNull(
        ExtensionLoader.loadExtensionClass(AbstractModelWriter.class, type.getWriterClazz()));
  }

  /*
   * PERCEPTRON_SEQUENCE needs a sequence stream to train, but shares its reader and
   * writer with PERCEPTRON, which is covered here.
   */
  @ParameterizedTest
  @EnumSource(value = AlgorithmType.class, names = "PERCEPTRON_SEQUENCE",
      mode = EnumSource.Mode.EXCLUDE)
  void testRoundTrip(AlgorithmType type) throws IOException {
    TrainingParameters params = new TrainingParameters();
    params.put(Parameters.ALGORITHM_PARAM, type.getAlgorithmType());
    params.put(Parameters.ITERATIONS_PARAM, 10);
    params.put(Parameters.CUTOFF_PARAM, 0);

    EventTrainer<TrainingParameters> trainer = TrainerFactory.getEventTrainer(params, null);
    AbstractModel trained = (AbstractModel) trainer.train(ObjectStreamUtils.createObjectStream(EVENTS));

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    GenericModelWriter writer = new GenericModelWriter(trained, new DataOutputStream(bytes));
    writer.persist();
    writer.close();

    AbstractModel read = new GenericModelReader(new BinaryFileDataReader(
        new ByteArrayInputStream(bytes.toByteArray()))).getModel();

    Assertions.assertEquals(trained.getClass(), read.getClass());
    Assertions.assertEquals(trained.getModelType(), read.getModelType());
    Assertions.assertEquals(trained.getNumOutcomes(), read.getNumOutcomes());
    for (Event e : EVENTS) {
      Assertions.assertArrayEquals(trained.eval(e.getContext()), read.eval(e.getContext()), 1e-6);
    }
  }
}
