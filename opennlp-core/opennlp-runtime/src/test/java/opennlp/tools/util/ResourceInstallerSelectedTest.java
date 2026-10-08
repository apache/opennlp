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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests {@link ResourceInstaller#installSelected(Path, ResourceInstaller.StagingStep,
 * Function)} with staging steps that write files directly.
 */
public class ResourceInstallerSelectedTest {

  private static final String KEEP_SUFFIX = ".keep";

  /**
   * Maps files ending in {@value #KEEP_SUFFIX} to their base names and skips the rest.
   *
   * @param relative The staged file's relative path.
   * @return The flattened destination, or {@code null} to skip the file.
   */
  private static Path keepFlattened(Path relative) {
    return relative.getFileName().toString().endsWith(KEEP_SUFFIX)
        ? relative.getFileName() : null;
  }

  @Test
  void testSelectedFilesAreFlattenedIntoTheTarget(@TempDir Path target)
      throws IOException {
    final int installed = ResourceInstaller.installSelected(target, staging -> {
      Files.createDirectories(staging.resolve("nested"));
      Files.writeString(staging.resolve("nested/a.keep"), "a");
      Files.writeString(staging.resolve("b.keep"), "b");
      Files.writeString(staging.resolve("c.skip"), "c");
    }, ResourceInstallerSelectedTest::keepFlattened);

    Assertions.assertEquals(2, installed);
    Assertions.assertEquals(List.of("a.keep", "b.keep"), list(target));
    Assertions.assertEquals("a", Files.readString(target.resolve("a.keep")));
  }

  @Test
  void testIdentitySelectorKeepsTheRelativeStructure(@TempDir Path target)
      throws IOException {
    final int installed = ResourceInstaller.installSelected(target, staging -> {
      Files.createDirectories(staging.resolve("nested"));
      Files.writeString(staging.resolve("nested/a.txt"), "a");
    }, Function.identity());

    Assertions.assertEquals(1, installed);
    Assertions.assertEquals("a", Files.readString(target.resolve("nested/a.txt")));
  }

  @Test
  void testTwoFilesMappingToOneDestinationPromoteNothing(@TempDir Path target)
      throws IOException {
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.installSelected(target, staging -> {
          Files.createDirectories(staging.resolve("d"));
          Files.writeString(staging.resolve("a.keep"), "1");
          Files.writeString(staging.resolve("d/a.keep"), "2");
        }, ResourceInstallerSelectedTest::keepFlattened));

    Assertions.assertEquals("two staged files install to the same path: a.keep",
        e.getMessage());
    Assertions.assertEquals(List.of(), list(target));
  }

  @Test
  void testFailingStepRemovesTheTargetItCreated(@TempDir Path parent) {
    final Path target = parent.resolve("out");

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.installSelected(target, staging -> {
          Files.writeString(staging.resolve("a.keep"), "a");
          throw new IOException("fetch failed");
        }, Function.identity()));

    Assertions.assertEquals("fetch failed", e.getMessage());
    Assertions.assertTrue(Files.notExists(target));
  }

  @Test
  void testFailingStepKeepsAnExistingTarget(@TempDir Path target) throws IOException {
    Files.writeString(target.resolve("existing.txt"), "x");

    Assertions.assertThrows(IllegalStateException.class,
        () -> ResourceInstaller.installSelected(target, staging -> {
          throw new IllegalStateException("step failed");
        }, Function.identity()));

    Assertions.assertEquals(List.of("existing.txt"), list(target));
  }

  /**
   * Checks that a destination outside the target aborts before anything is moved.
   *
   * @param destination The destination the selector returns.
   * @param parent A scratch directory managed by the test framework.
   * @throws IOException Thrown if setting up or listing the directories fails.
   */
  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"../x", "/abs", "a/../../x", "./x", ""})
  void testDestinationLeavingTheTargetIsRejected(String destination,
      @TempDir Path parent) throws IOException {
    final Path target = Files.createDirectory(parent.resolve("out"));

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.installSelected(target,
            staging -> Files.writeString(staging.resolve("a.keep"), "a"),
            relative -> Path.of(destination)));

    Assertions.assertEquals("selected destination leaves the target: " + destination,
        e.getMessage());
    Assertions.assertEquals(List.of(), list(target));
    Assertions.assertEquals(List.of("out"), list(parent));
  }

  @Test
  void testExistingDestinationAbortsBeforeTheFirstMove(@TempDir Path target)
      throws IOException {
    Files.writeString(target.resolve("b.keep"), "old");

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.installSelected(target, staging -> {
          Files.writeString(staging.resolve("a.keep"), "a");
          Files.writeString(staging.resolve("b.keep"), "b");
        }, ResourceInstallerSelectedTest::keepFlattened));

    Assertions.assertEquals("target already contains: " + target.resolve("b.keep"),
        e.getMessage());
    Assertions.assertEquals(List.of("b.keep"), list(target));
    Assertions.assertEquals("old", Files.readString(target.resolve("b.keep")));
  }

  /**
   * Checks that each parameter is required.
   *
   * @param argument The parameter passed as {@code null}.
   * @param target A scratch directory managed by the test framework.
   */
  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"targetDirectory", "step", "selector"})
  void testNullArgumentsAreRejected(String argument, @TempDir Path target) {
    final ResourceInstaller.StagingStep step = staging -> { };
    final Executable call = switch (argument) {
      case "targetDirectory" -> () -> ResourceInstaller.installSelected(
          null, step, Function.identity());
      case "step" -> () -> ResourceInstaller.installSelected(
          target, null, Function.identity());
      case "selector" -> () -> ResourceInstaller.installSelected(target, step, null);
      default -> throw new IllegalArgumentException("unknown argument: " + argument);
    };

    final IllegalArgumentException e =
        Assertions.assertThrows(IllegalArgumentException.class, call);
    Assertions.assertEquals(argument + " must not be null", e.getMessage());
  }

  /**
   * Lists the names directly inside a directory, including hidden ones.
   *
   * @param directory The directory to list.
   * @return The sorted entry names. Not {@code null}.
   * @throws IOException Thrown if listing fails.
   */
  private static List<String> list(Path directory) throws IOException {
    try (Stream<Path> entries = Files.list(directory)) {
      return entries.map(path -> path.getFileName().toString()).sorted().toList();
    }
  }
}
