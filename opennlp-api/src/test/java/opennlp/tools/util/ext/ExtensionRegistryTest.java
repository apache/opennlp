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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExtensionRegistryTest {

  interface Greeter {
    String greet();
  }

  static final class HelloGreeter implements Greeter {
    @Override
    public String greet() {
      return "hello";
    }
  }

  static final class PrefixedGreeter implements Greeter {
    private final String prefix;

    PrefixedGreeter(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public String greet() {
      return prefix + " hello";
    }
  }

  @Test
  void testEmptyRegistry() {
    ExtensionRegistry registry = new ExtensionRegistry();
    assertTrue(registry.names().isEmpty());
    assertFalse(registry.isRegistered(HelloGreeter.class.getName()));
    assertNull(registry.supplier(HelloGreeter.class.getName()));
    assertNull(registry.implementation(HelloGreeter.class.getName()));
    assertNull(registry.factory(HelloGreeter.class.getName(), Function.class));
    assertFalse(registry.isRegistered(null));
    assertNull(registry.supplier(null));
    assertNull(registry.implementation(null));
    assertNull(registry.factory(null, Function.class));
  }

  @Test
  void testRegisterSupplier() {
    ExtensionRegistry registry = new ExtensionRegistry();
    AtomicInteger calls = new AtomicInteger();
    registry.register(HelloGreeter.class, () -> {
      calls.incrementAndGet();
      return new HelloGreeter();
    });

    String name = HelloGreeter.class.getName();
    assertTrue(registry.isRegistered(name));
    assertSame(HelloGreeter.class, registry.implementation(name));
    assertEquals(1, registry.names().size());
    Supplier<?> supplier = registry.supplier(name);
    assertNotNull(supplier);
    assertEquals("hello", ((Greeter) supplier.get()).greet());
    assertEquals(1, calls.get());
  }

  @Test
  void testRegisterReplacesSupplier() {
    ExtensionRegistry registry = new ExtensionRegistry();
    registry.register(HelloGreeter.class, HelloGreeter::new);
    HelloGreeter singleton = new HelloGreeter();
    registry.register(HelloGreeter.class, () -> singleton);

    assertSame(singleton, registry.supplier(HelloGreeter.class.getName()).get());
    assertEquals(1, registry.names().size());
  }

  @Test
  void testRegisterTypedFactory() {
    ExtensionRegistry registry = new ExtensionRegistry();
    registry.register(PrefixedGreeter.class, Function.class,
        (Function<String, Greeter>) PrefixedGreeter::new);

    String name = PrefixedGreeter.class.getName();
    assertTrue(registry.isRegistered(name));
    assertNull(registry.supplier(name));
    @SuppressWarnings("unchecked")
    Function<String, Greeter> factory = registry.factory(name, Function.class);
    assertNotNull(factory);
    assertEquals("well hello", factory.apply("well").greet());
    assertNull(registry.factory(name, Supplier.class));
  }

  @Test
  void testNamesIsUnmodifiable() {
    ExtensionRegistry registry = new ExtensionRegistry();
    registry.register(HelloGreeter.class, HelloGreeter::new);
    assertThrows(UnsupportedOperationException.class, () -> registry.names().clear());
  }

  @Test
  void testRegisterRejectsNullArguments() {
    ExtensionRegistry registry = new ExtensionRegistry();
    assertThrows(IllegalArgumentException.class, () -> registry.register(null, HelloGreeter::new));
    assertThrows(IllegalArgumentException.class, () -> registry.register(HelloGreeter.class, null));
    assertThrows(IllegalArgumentException.class,
        () -> registry.register(null, Function.class, (Function<String, Greeter>) PrefixedGreeter::new));
    assertThrows(IllegalArgumentException.class,
        () -> registry.register(PrefixedGreeter.class, null,
            (Function<String, Greeter>) PrefixedGreeter::new));
    assertThrows(IllegalArgumentException.class,
        () -> registry.register(PrefixedGreeter.class, Function.class, null));
    assertThrows(IllegalArgumentException.class,
        () -> registry.factory(HelloGreeter.class.getName(), null));
  }

  @Test
  void testDefaultRegistryDiscoversRegistrars() {
    ExtensionRegistry registry = ExtensionRegistry.getDefault();
    assertSame(registry, ExtensionRegistry.getDefault());
    String name = TestExtensionRegistrar.RegisteredExtension.class.getName();
    assertTrue(registry.isRegistered(name), "the test registrar should have been discovered");
    assertSame(TestExtensionRegistrar.RegisteredExtension.class, registry.implementation(name));
  }
}
