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

package opennlp.tools.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the GraalVM reachability metadata of this module in step with its
 * resources: every file under {@code src/main/resources/opennlp} must be covered
 * by a resource glob, or a native image cannot read it.
 */
public class ReachabilityMetadataTest {

  private static final Path RESOURCES = Path.of("src", "main", "resources");
  private static final Path METADATA = RESOURCES.resolve(Path.of("META-INF", "native-image",
      "org.apache.opennlp", "opennlp-runtime", "reachability-metadata.json"));

  private static List<PathMatcher> globs;

  @BeforeAll
  static void readGlobs() throws IOException {
    assertTrue(Files.isRegularFile(METADATA), "expected " + METADATA.toAbsolutePath());
    globs = new ArrayList<>();
    final String json = Files.readString(METADATA, StandardCharsets.UTF_8);
    final String key = "\"glob\"";
    int at = json.indexOf(key);
    while (at >= 0) {
      int open = json.indexOf('"', at + key.length());
      int close = json.indexOf('"', open + 1);
      globs.add(FileSystems.getDefault().getPathMatcher("glob:" + json.substring(open + 1, close)));
      at = json.indexOf(key, close);
    }
    assertFalse(globs.isEmpty(), "no resource globs in " + METADATA);
  }

  static Stream<Path> bundledResources() throws IOException {
    try (Stream<Path> files = Files.walk(RESOURCES.resolve("opennlp"))) {
      return files.filter(Files::isRegularFile).map(RESOURCES::relativize).toList().stream();
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("bundledResources")
  void testResourceIsCoveredByMetadata(Path resource) {
    assertTrue(globs.stream().anyMatch(g -> g.matches(resource)),
        resource + " is not covered by a resource glob in " + METADATA);
  }

  @Test
  void testMetadataDoesNotClaimClasses() {
    Path classFile = Path.of("opennlp", "tools", "util", "Version.class");
    assertFalse(globs.stream().anyMatch(g -> g.matches(classFile)),
        "resource globs must not match class files");
  }
}
