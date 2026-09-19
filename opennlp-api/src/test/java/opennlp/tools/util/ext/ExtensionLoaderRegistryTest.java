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

package opennlp.tools.util.ext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the registry-first path of {@link ExtensionLoader#instantiateExtension(Class, String)}.
 */
public class ExtensionLoaderRegistryTest {

  @Test
  void testRegisteredExtensionIsCreatedBySupplier() {
    int before = TestExtensionRegistrar.CREATED.get();
    TestExtensionRegistrar.RegisteredExtension ext = ExtensionLoader.instantiateExtension(
        TestExtensionRegistrar.RegisteredExtension.class,
        TestExtensionRegistrar.RegisteredExtension.class.getName());
    assertNotNull(ext);
    assertEquals("registered", ext.value());
    assertEquals(before + 1, TestExtensionRegistrar.CREATED.get());
  }

  @Test
  void testRegisteredExtensionOfWrongTypeIsRejected() {
    ExtensionNotLoadedException e = assertThrows(ExtensionNotLoadedException.class,
        () -> ExtensionLoader.instantiateExtension(Runnable.class,
            TestExtensionRegistrar.RegisteredExtension.class.getName()));
    assertTrue(e.getMessage().contains("needs to have type"));
  }

  @Test
  void testUnregisteredExtensionMessageNamesTheRegistrar() {
    ExtensionNotLoadedException e = assertThrows(ExtensionNotLoadedException.class,
        () -> ExtensionLoader.instantiateExtension(Runnable.class, "opennlp.tools.util.ext.Missing"));
    assertTrue(e.getMessage().contains("ExtensionRegistrar"));
  }
}
