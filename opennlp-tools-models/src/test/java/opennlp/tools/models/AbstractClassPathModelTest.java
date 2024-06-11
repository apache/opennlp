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

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class AbstractClassPathModelTest {

  protected ClassPathModel getClassPathModel(String modelJarPrefix) throws IOException {
    return getClassPathModel(modelJarPrefix, false);
  }

  protected ClassPathModel getClassPathModel(String modelJarPrefix, boolean expectNotFound)
      throws IOException {
    final ClassPathModelFinder finder = getModelFinder(modelJarPrefix);

    final Set<ClassPathModelEntry> models = finder.findModels(false);
    assertNotNull(models);
    if (expectNotFound) {
      assertEquals(0, models.size());
      return null;
    } else {
      assertFalse(models.isEmpty());
      final ClassPathModelEntry entry = models.iterator().next();
      assertNotNull(entry);
      final ClassPathModelLoader loader = new ClassPathModelLoader();
      final ClassPathModel model = loader.load(entry);
      assertNotNull(model);
      assertNotNull(model.model());
      assertNotNull(model.properties());
      return model;
    }
  }

  protected abstract ClassPathModelFinder getModelFinder();

  protected abstract ClassPathModelFinder getModelFinder(String pattern);
}
