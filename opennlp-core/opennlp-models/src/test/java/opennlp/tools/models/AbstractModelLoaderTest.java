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
package opennlp.tools.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class AbstractModelLoaderTest extends AbstractClassPathModelTest {

  @Test
  public void testLoadOpenNLPModel() throws Exception {
    final ClassPathModel model = getClassPathModel("opennlp-models-langdetect-*.jar");
    assertNotNull(model);
    assertNotNull(model.model());
    assertNotNull(model.properties());
    assertEquals("2ddf585fac2e02a9dcfb9a4a9cc9417562eaac351be2efb506a2eaa87f19e9d4",
        model.getModelSHA256());
    assertEquals("langdetect-183.bin", model.getModelName());
    assertEquals("1.8.3", model.getModelVersion());
    assertEquals("root", model.getModelLanguage());
  }

}
