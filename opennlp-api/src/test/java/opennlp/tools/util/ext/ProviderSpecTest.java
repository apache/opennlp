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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderSpecTest {

  private static final String ONNX = ".onnx";

  @Test
  void testEmpty() {
    final ProviderSpec spec = ProviderSpec.empty();
    assertSame(spec, ProviderSpec.empty());
    assertTrue(spec.location().isEmpty());
    assertTrue(spec.path().isEmpty());
    assertTrue(spec.options().isEmpty());
    assertFalse(spec.locationEndsWith(ONNX));
    assertTrue(spec.hasOnlyOptions());
  }

  @Test
  void testOptionsAreCopiedInOrderAndUnmodifiable() {
    final Map<String, String> options = new LinkedHashMap<>();
    options.put("b", "2");
    options.put("a", "1");
    final ProviderSpec spec = ProviderSpec.of(options);
    options.put("c", "3");
    assertEquals(List.of("b", "a"), List.copyOf(spec.options().keySet()));
    assertThrows(UnsupportedOperationException.class, () -> spec.options().put("c", "3"));
    assertEquals("1", spec.option("a", null));
    assertEquals("default", spec.option("c", "default"));
    assertNull(spec.option("c", null));
  }

  @Test
  void testRejectsInvalidArguments() {
    final Map<String, String> nullKey = new HashMap<>();
    nullKey.put(null, "value");
    final Map<String, String> nullValue = new HashMap<>();
    nullValue.put("key", null);
    assertThrows(IllegalArgumentException.class, () -> ProviderSpec.of((Map<String, String>) null));
    assertThrows(IllegalArgumentException.class, () -> ProviderSpec.of(nullKey));
    assertThrows(IllegalArgumentException.class, () -> ProviderSpec.of(nullValue));
    assertThrows(IllegalArgumentException.class, () -> ProviderSpec.of(Map.of(" ", "value")));
    assertThrows(IllegalArgumentException.class, () -> ProviderSpec.of(Map.of("", "value")));
    assertThrows(IllegalArgumentException.class, () -> ProviderSpec.of((URI) null));
    assertThrows(IllegalArgumentException.class, () -> ProviderSpec.of((Path) null));
    assertThrows(IllegalArgumentException.class, () -> ProviderSpec.of((Path) null, Map.of()));
    assertThrows(IllegalArgumentException.class, () -> ProviderSpec.of(URI.create("model.onnx")));
    assertThrows(IllegalArgumentException.class, () -> ProviderSpec.of(Path.of("model.onnx"), null));
    final ProviderSpec spec = ProviderSpec.empty();
    assertThrows(IllegalArgumentException.class, () -> spec.option(null, "value"));
    assertThrows(IllegalArgumentException.class, () -> spec.hasOnlyOptions((String[]) null));
    assertThrows(IllegalArgumentException.class, () -> spec.hasOnlyOptions("a", null));
    assertThrows(IllegalArgumentException.class, () -> spec.locationEndsWith(null));
    assertThrows(IllegalArgumentException.class, () -> spec.locationEndsWith(""));
  }

  @Test
  void testRelativePathResolvesAgainstWorkingDirectory() {
    final ProviderSpec spec = ProviderSpec.of(Path.of("models", "model.onnx"));
    assertTrue(spec.location().orElseThrow().isAbsolute());
    assertEquals(Path.of("models", "model.onnx").toAbsolutePath(), spec.path().orElseThrow());
    assertTrue(spec.locationEndsWith(ONNX));
  }

  @Test
  void testPathOnlyForLocalFiles() {
    final Path file = Path.of("model.onnx").toAbsolutePath();
    assertEquals(file, ProviderSpec.of(file.toUri()).path().orElseThrow());
    assertTrue(ProviderSpec.of(URI.create("jar:file:/models.jar!/model.onnx")).path().isEmpty());
    assertTrue(ProviderSpec.of(URI.create("https://example.org/model.onnx")).path().isEmpty());
    assertTrue(ProviderSpec.of(URI.create("file:model.onnx")).path().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "file:/models/model.onnx",
      "file:/models/MODEL.ONNX",
      "file:/models/.onnx",
      "jar:file:/models.jar!/nested/model.onnx",
      "https://example.org/models/model.onnx?revision=1",
      "classpath:model.onnx"
  })
  void testLocationEndsWith(final String location) {
    assertTrue(ProviderSpec.of(URI.create(location)).locationEndsWith(ONNX));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "file:/models/model.bin",
      "file:/models/model.onnx/",
      "file:/models.onnx/model",
      "file:/models/onnx",
      "https://example.org/model.bin#model.onnx",
      "https://example.org"
  })
  void testLocationDoesNotEndWith(final String location) {
    assertFalse(ProviderSpec.of(URI.create(location)).locationEndsWith(ONNX));
  }

  @Test
  void testHasOnlyOptions() {
    final ProviderSpec spec = ProviderSpec.of(Map.of("vocabulary", "vocab.txt"));
    assertTrue(spec.hasOnlyOptions("vocabulary"));
    assertTrue(spec.hasOnlyOptions("lowerCase", "vocabulary"));
    assertFalse(spec.hasOnlyOptions("lowerCase"));
    assertFalse(spec.hasOnlyOptions());
  }

  @Test
  void testPathIsNotNormalized() {
    final Path traversing = Path.of("models", "current", "..", "weights", "model.onnx");
    final ProviderSpec spec = ProviderSpec.of(traversing);
    assertEquals(traversing.toAbsolutePath(), spec.path().orElseThrow(),
        "a path is kept as it is, as its segments may be symbolic links");
    assertTrue(spec.location().orElseThrow().toString().endsWith("/current/../weights/model.onnx"),
        spec.location().orElseThrow().toString());
  }

  @Test
  void testLocationEndsWithDecodesThePath() {
    assertTrue(ProviderSpec.of(URI.create("file:/models/a%3Fb.onnx")).locationEndsWith(ONNX),
        "a question mark in a file name is not a query");
    assertFalse(ProviderSpec.of(URI.create("file:/models/a.onnx%3Fb")).locationEndsWith(ONNX),
        "an encoded question mark does not end the name either");
    assertTrue(ProviderSpec.of(Path.of("/models/gpt+2.onnx")).locationEndsWith("+2.onnx"),
        "a plus is part of the file name, not a space");
    assertFalse(ProviderSpec.of(URI.create("file:/models/evil%00.onnx")).locationEndsWith(ONNX),
        "a control character in the path never matches");
  }

  @Test
  void testLocationEndsWithKeepsUnescapedCharacters() {
    assertTrue(ProviderSpec.of(Path.of("/my models/modèle.onnx")).locationEndsWith("modèle.onnx"),
        "a non-ASCII character next to an escaped space");
    assertTrue(ProviderSpec.of(Path.of("/my models/中文.onnx")).locationEndsWith("中文.onnx"),
        "a character beyond Latin-1 next to an escaped space");
    assertTrue(ProviderSpec.of(URI.create("file:/my%20models/mod%C3%A8le.onnx"))
        .locationEndsWith("modèle.onnx"), "escaped UTF-8");
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void testWindowsPaths() {
    final ProviderSpec drive = ProviderSpec.of(Path.of("C:\\models\\model.onnx"));
    assertEquals(Path.of("C:\\models\\model.onnx"), drive.path().orElseThrow(), "drive letter");
    assertTrue(drive.locationEndsWith(ONNX), "drive letter");
    final ProviderSpec unc = ProviderSpec.of(Path.of("\\\\server\\share\\model.onnx"));
    assertEquals(Path.of("\\\\server\\share\\model.onnx"), unc.path().orElseThrow(), "UNC path");
    assertTrue(unc.locationEndsWith(ONNX), "UNC path");
  }

  @Test
  void testPathDoesNotDependOnTheFileSystem(@TempDir final Path dir) throws IOException {
    final Path model = dir.resolve("model.onnx");
    final ProviderSpec absent = ProviderSpec.of(model);
    Files.createDirectory(model);
    final ProviderSpec directory = ProviderSpec.of(model);
    assertEquals(absent, directory, "a spec must not change when the location is created");
    assertTrue(directory.locationEndsWith(ONNX), "a model directory keeps its name");
    assertEquals(model, directory.path().orElseThrow(), "path");
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "jar:file:/models.jar!/model.bin?revision=.onnx",
      "https://example.org/model.bin?revision=.onnx",
      "file:/models/model.onnx.bin",
      "file:/models/onnx"
  })
  void testLocationEndsWithIgnoresTheQuery(final String location) {
    assertFalse(ProviderSpec.of(URI.create(location)).locationEndsWith(ONNX), location);
  }

  @Test
  void testLocationEndsWithWholeName() {
    assertTrue(ProviderSpec.of(URI.create("file:/models/.onnx")).locationEndsWith(ONNX),
        "a name that is the suffix");
    assertTrue(ProviderSpec.of(URI.create("file:/models/model.onnx")).locationEndsWith("onnx"),
        "a suffix without a separator");
    assertFalse(ProviderSpec.of(URI.create("file:/x.onnx")).locationEndsWith("/models/x.onnx"),
        "a suffix longer than the path");
  }

  @Test
  void testEqualsIgnoresOptionOrder() {
    final Map<String, String> ordered = new LinkedHashMap<>();
    ordered.put("b", "2");
    ordered.put("a", "1");
    assertEquals(ProviderSpec.of(Map.of("a", "1", "b", "2")), ProviderSpec.of(ordered),
        "options are equal regardless of their order");
  }

  @Test
  void testEqualsAndHashCode() {
    final URI location = URI.create("file:/models/model.onnx");
    final ProviderSpec spec = ProviderSpec.of(location, Map.of("a", "1"));
    assertEquals(spec, ProviderSpec.of(location, Map.of("a", "1")));
    assertEquals(spec.hashCode(), ProviderSpec.of(location, Map.of("a", "1")).hashCode());
    assertNotEquals(spec, ProviderSpec.of(location, Map.of("a", "2")));
    assertNotEquals(spec, ProviderSpec.of(Map.of("a", "1")));
    assertNotEquals(spec, null);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "https://user:secret@example.org/models/model.onnx",
      "https://example.org/models/model.onnx?signature=secret",
      "jar:file:/models.jar!/model.onnx?signature=secret",
      "https://user:secret@my_host.internal/models/model.onnx",
      "jar:https://user:secret@example.org/models.jar!/model.onnx",
      "https://example.org/models/model.onnx#secret",
      "ftp:user:secret@example.org/models/model.onnx"
  })
  void testToStringOmitsCredentials(final String location) {
    final String text = ProviderSpec.of(URI.create(location), Map.of("apiKey", "secret")).toString();
    assertTrue(text.contains("model.onnx"), text);
    assertTrue(text.contains("apiKey"), text);
    assertFalse(text.contains("secret"), text);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "https://user:secret@example.org/models/providers.properties",
      "jar:https://user:secret@example.org/lib.jar!/META-INF/opennlp/providers.properties",
      "ftp:user:secret@example.org/providers.properties"
  })
  void testPrintableOmitsCredentials(final String location) {
    final String text = ProviderSpec.printable(URI.create(location));
    assertTrue(text.contains("providers.properties"), text);
    assertFalse(text.contains("secret"), text);
  }

  @Test
  void testToStringKeepsThePath() {
    final String text = ProviderSpec.of(URI.create("https://tok@example.org/tok@m/model.onnx"))
        .toString();
    assertTrue(text.contains("/tok@m/model.onnx"), text);
  }
}
