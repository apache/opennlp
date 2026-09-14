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
package opennlp.embeddings;

import org.junit.jupiter.api.Test;

import opennlp.embeddings.spi.TeacherEncoderProviders;
import opennlp.tools.embeddings.TextEmbedderProviders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Checks the dependencies supplied by the default embedding bundle. */
class DefaultEmbeddingProvidersTest {

  @Test
  void includesBothProvidersButNoCli() {
    assertEquals("onnx", TextEmbedderProviders.getDefault().name());
    assertEquals("static", TextEmbedderProviders.get("static").name());
    assertEquals("onnx", TeacherEncoderProviders.getDefault().name());
    assertThrows(ClassNotFoundException.class,
        () -> Class.forName("opennlp.tools.cmdline.CmdLineTool", false, getClass().getClassLoader()));
  }
}
