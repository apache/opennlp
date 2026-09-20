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

package opennlp.tools.depparse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

import static opennlp.tools.depparse.DependencyTestSamples.corpus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exercises the standard factory and model persistence contract. */
public class DependencyParserFactoryTest {

  @Test
  void testFactoryRoundTrip() throws IOException {
    TrainingParameters parameters = TrainingParameters.defaultParams();
    parameters.put(Parameters.CUTOFF_PARAM, 0);
    DependencyModel model = DependencyParserME.train("eng",
        ObjectStreamUtils.createObjectStream(corpus()), parameters,
        DependencyParserFactory.create(TestDependencyParserFactory.class.getName()));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    DependencyModel loaded = new DependencyModel(new ByteArrayInputStream(out.toByteArray()));
    assertInstanceOf(TestDependencyParserFactory.class, loaded.getFactory());
    assertEquals("test-value", loaded.getManifestProperty("test-entry"));
    assertEquals("eng", loaded.getLanguage());
    DependencySample sample = corpus().get(0);
    assertEquals(sample.getGraph(), new DependencyParserME(loaded)
        .parse(sample.getTokens(), sample.getTags()));
  }

  @Test
  void testDefaultAndInvalidFactories() throws IOException {
    assertInstanceOf(DependencyParserFactory.class, DependencyParserFactory.create(null));
    assertThrows(InvalidFormatException.class,
        () -> DependencyParserFactory.create(String.class.getName()));
    assertThrows(IllegalArgumentException.class,
        () -> DependencyParserME.train("eng", ObjectStreamUtils.createObjectStream(corpus()),
            TrainingParameters.defaultParams(), null));
  }
}
