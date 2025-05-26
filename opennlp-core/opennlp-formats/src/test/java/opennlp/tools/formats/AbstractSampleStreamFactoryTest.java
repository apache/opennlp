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

package opennlp.tools.formats;

import org.junit.jupiter.api.Test;

import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.util.ObjectStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public abstract class AbstractSampleStreamFactoryTest<S, P> extends AbstractFormatTest {

  protected static final String FORMAT_SAMPLE_DIR = "opennlp/tools/formats/";

  protected abstract AbstractSampleStreamFactory<S, P> getFactory();
  protected abstract String getDataFilePath();

  @Test
  void testCreateWithNullParameter() {
    assertThrows(IllegalArgumentException.class, () -> {
      try (ObjectStream<S> stream = getFactory().create(null)) {
        stream.read();
      }
    });
  }

  @Test
  void testCreateWithInvalidParameter() {
    assertThrows(IllegalArgumentException.class, () -> {
      try (ObjectStream<S> stream = getFactory().create(new String[]{"X"})) {
        stream.read();
      }
    });
  }

  /*
   * Note:
   * This test case must be overridden for non-simple cases where
   * more than the '-data' param is required.
   */
  @Test
  protected void testCreateWithInvalidDataFilePath() {
    assertThrows(TerminateToolException.class, () -> {
      try (ObjectStream<S> stream = getFactory().create(
              new String[]{"-data", getDataFilePath() + "xyz"})) {
        S sample = stream.read();
        assertNotNull(sample);
      }
    });
  }
}
