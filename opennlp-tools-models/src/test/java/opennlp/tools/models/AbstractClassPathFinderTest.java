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

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class AbstractClassPathFinderTest extends AbstractClassPathModelTest {

  @Test
  public void testFindOpenNLPModels() {
    final ClassPathModelFinder finder = getModelFinder();

    final Set<ClassPathModelEntry> models = finder.findModels(false);
    assertNotNull(models);
    assertEquals(4, models.size());

    for (ClassPathModelEntry entry : models) {
      assertNotNull(entry.model());
      assertNotNull(entry.properties());
      assertFalse(entry.properties().isEmpty());
    }

    //call it twice, yields same results
    final Set<ClassPathModelEntry> reloadedModels = finder.findModels(false);
    assertNotNull(reloadedModels);
    assertEquals(models, reloadedModels);

    //call it with reload cache, yields same results
    final Set<ClassPathModelEntry> cacheReloadedModels = finder.findModels(true);
    assertNotNull(cacheReloadedModels);
    assertEquals(models, cacheReloadedModels);
    assertEquals(reloadedModels, cacheReloadedModels);
  }

}
