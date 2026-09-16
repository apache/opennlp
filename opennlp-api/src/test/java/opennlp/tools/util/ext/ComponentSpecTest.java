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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentSpecTest {

  @Test
  void testEmptySpecHasNoLocationAndNoOptions() {
    ComponentSpec spec = ComponentSpec.empty();
    assertNull(spec.location());
    assertTrue(spec.options().isEmpty());
    assertSame(spec, ComponentSpec.empty());
    assertTrue(spec.hasOnlyOptions());
    assertFalse(spec.locationEndsWith(".onnx"));
  }

  @Test
  void testOptionsAreCopiedUnmodifiableAndOrdered() {
    Map<String, String> options = new LinkedHashMap<>();
    options.put("b", "2");
    options.put("a", "1");
    ComponentSpec spec = ComponentSpec.of(Path.of("model.onnx"), options);
    options.put("c", "3");
    assertEquals(List.of("b", "a"), List.copyOf(spec.options().keySet()));
    assertThrows(UnsupportedOperationException.class, () -> spec.options().put("d", "4"));
    assertEquals("1", spec.option("a", "x"));
    assertEquals("x", spec.option("missing", "x"));
    assertNull(spec.option("missing", null));
    assertThrows(IllegalArgumentException.class, () -> spec.option(null, "x"));
  }

  @Test
  void testRejectsNullArguments() {
    assertThrows(IllegalArgumentException.class, () -> ComponentSpec.of((Path) null));
    assertThrows(IllegalArgumentException.class, () -> ComponentSpec.of((Map<String, String>) null));
    assertThrows(IllegalArgumentException.class, () -> ComponentSpec.of(Path.of("m"), null));
    Map<String, String> nullValue = new HashMap<>();
    nullValue.put("k", null);
    assertThrows(IllegalArgumentException.class, () -> ComponentSpec.of(nullValue));
    Map<String, String> nullKey = new HashMap<>();
    nullKey.put(null, "v");
    assertThrows(IllegalArgumentException.class, () -> ComponentSpec.of(nullKey));
    ComponentSpec spec = ComponentSpec.empty();
    assertThrows(IllegalArgumentException.class, () -> spec.withOption(null, "v"));
    assertThrows(IllegalArgumentException.class, () -> spec.withOption("k", null));
    assertThrows(IllegalArgumentException.class, () -> spec.hasOnlyOptions((String[]) null));
    assertThrows(IllegalArgumentException.class, () -> spec.locationEndsWith(null));
  }

  @Test
  void testHasOnlyOptionsChecksEveryName() {
    ComponentSpec spec = ComponentSpec.of(Map.of("vocabulary", "v.txt", "lowerCase", "false"));
    assertTrue(spec.hasOnlyOptions("vocabulary", "lowerCase"));
    assertTrue(spec.hasOnlyOptions("lowerCase", "vocabulary", "unused"));
    assertFalse(spec.hasOnlyOptions("vocabulary"));
    assertFalse(spec.hasOnlyOptions());
  }

  @ParameterizedTest
  @ValueSource(strings = {"model.onnx", "MODEL.ONNX", "dir/model.Onnx", ".onnx"})
  void testLocationEndsWithIgnoresCase(String location) {
    assertTrue(ComponentSpec.of(Path.of(location)).locationEndsWith(".onnx"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"model.bin", "onnx", "model.onnx.bak", "/"})
  void testLocationEndsWithRejectsOtherNames(String location) {
    assertFalse(ComponentSpec.of(Path.of(location)).locationEndsWith(".onnx"));
  }

  @Test
  void testWithOptionReturnsANewSpec() {
    ComponentSpec base = ComponentSpec.of(Path.of("m"), Map.of("a", "1"));
    ComponentSpec more = base.withOption("b", "2");
    ComponentSpec replaced = more.withOption("a", "9");
    assertEquals(Map.of("a", "1"), base.options());
    assertEquals(List.of("a", "b"), List.copyOf(more.options().keySet()));
    assertEquals("9", replaced.option("a", null));
    assertEquals(Path.of("m"), replaced.location());
  }

  @Test
  void testEqualityCoversLocationAndOptions() {
    ComponentSpec a = ComponentSpec.of(Path.of("m"), Map.of("k", "v"));
    ComponentSpec b = ComponentSpec.of(Path.of("m"), Map.of("k", "v"));
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, ComponentSpec.of(Path.of("n"), Map.of("k", "v")));
    assertNotEquals(a, ComponentSpec.of(Path.of("m"), Map.of("k", "w")));
    assertNotEquals(a, ComponentSpec.of(Map.of("k", "v")));
    assertTrue(a.toString().contains("k=v"));
  }
}
