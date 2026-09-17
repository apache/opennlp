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

package opennlp.tools.models.simple;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.models.ClassPathModelEntry;
import opennlp.tools.util.jvm.NativeImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In a native image the finder has no class path to scan and must report that
 * instead of failing.
 */
public class SimpleClassPathModelFinderNativeImageTest {

  @AfterEach
  void reset() {
    System.clearProperty(NativeImage.IMAGE_CODE_PROPERTY);
  }

  @Test
  void testFindsModelsOnTheJvm() {
    Set<ClassPathModelEntry> models = new SimpleClassPathModelFinder().findModels(true);
    assertFalse(models.isEmpty());
  }

  @Test
  void testFindsNothingInAnImage() {
    System.setProperty(NativeImage.IMAGE_CODE_PROPERTY, "runtime");
    Set<ClassPathModelEntry> models = new SimpleClassPathModelFinder().findModels(true);
    assertNotNull(models);
    assertTrue(models.isEmpty());
  }
}
