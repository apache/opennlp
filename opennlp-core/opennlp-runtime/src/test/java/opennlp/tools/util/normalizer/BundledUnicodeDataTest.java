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
package opennlp.tools.util.normalizer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BundledUnicodeDataTest {

  private static final String MISSING = "no-such-bundled-data.txt";

  private static InputStream utf8(String text) {
    return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void testLoadParsesExistingResource() {
    final Integer lines = BundledUnicodeData.load(Confusables.class, "confusables.txt",
        "confusables", in -> {
          final AtomicInteger count = new AtomicInteger();
          BundledUnicodeData.forEachContentLine(in, (content, lineNumber) -> count.incrementAndGet());
          return count.get();
        });
    assertTrue(lines > 0);
  }

  @Test
  void testLoadRejectsMissingResource() {
    final IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> BundledUnicodeData.load(BundledUnicodeDataTest.class, MISSING, "test",
            in -> "unreachable"));
    assertEquals("Missing test data resource: " + MISSING, e.getMessage());
  }

  @Test
  void testLoadWrapsReadFailure() {
    final IOException cause = new IOException("boom");
    final UncheckedIOException e = assertThrows(UncheckedIOException.class,
        () -> BundledUnicodeData.load(Confusables.class, "confusables.txt", "test", in -> {
          throw cause;
        }));
    assertEquals("Unable to read test data resource confusables.txt", e.getMessage());
    assertSame(cause, e.getCause());
  }

  @Test
  void testLoadPassesParserFailureThrough() {
    final IllegalArgumentException cause = new IllegalArgumentException("malformed");
    assertSame(cause, assertThrows(IllegalArgumentException.class,
        () -> BundledUnicodeData.load(Confusables.class, "confusables.txt", "test", in -> {
          throw cause;
        })));
  }

  static Stream<Arguments> nullArguments() {
    final BundledUnicodeData.Parser<String> parser = in -> "";
    final BundledUnicodeData.LineConsumer consumer = (line, lineNumber) -> { };
    return Stream.of(
        Arguments.of("owner", (Executable) () -> BundledUnicodeData.load(null, MISSING, "t", parser)),
        Arguments.of("resource", (Executable) () ->
            BundledUnicodeData.load(BundledUnicodeDataTest.class, null, "t", parser)),
        Arguments.of("description", (Executable) () ->
            BundledUnicodeData.load(BundledUnicodeDataTest.class, MISSING, null, parser)),
        Arguments.of("parser", (Executable) () ->
            BundledUnicodeData.load(BundledUnicodeDataTest.class, MISSING, "t", null)),
        Arguments.of("in", (Executable) () ->
            BundledUnicodeData.forEachLine(null, StandardCharsets.UTF_8, consumer)),
        Arguments.of("charset", (Executable) () ->
            BundledUnicodeData.forEachLine(utf8(""), null, consumer)),
        Arguments.of("consumer", (Executable) () ->
            BundledUnicodeData.forEachLine(utf8(""), StandardCharsets.UTF_8, null)),
        Arguments.of("in", (Executable) () -> BundledUnicodeData.forEachContentLine(null, consumer)),
        Arguments.of("consumer", (Executable) () ->
            BundledUnicodeData.forEachContentLine(utf8(""), null)),
        Arguments.of("loader", (Executable) () -> new BundledUnicodeData.Lazy<String>(null)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("nullArguments")
  void testNullArgumentIsRejected(String name, Executable call) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, call);
    assertEquals(name + " must not be null", e.getMessage());
  }

  @Test
  void testForEachLinePassesRawLinesWithNumbers() throws IOException {
    final List<String> seen = new ArrayList<>();
    BundledUnicodeData.forEachLine(utf8("# head\n\n  a ; b # c\n"), StandardCharsets.UTF_8,
        (line, lineNumber) -> seen.add(lineNumber + ":" + line));
    assertEquals(List.of("1:# head", "2:", "3:  a ; b # c"), seen);
  }

  @Test
  void testForEachContentLineStripsCommentsAndSkipsEmptyLines() throws IOException {
    final List<String> seen = new ArrayList<>();
    BundledUnicodeData.forEachContentLine(utf8("# head\n\n  0041 ; X # note\n   # only\n0042;Y"),
        (content, lineNumber) -> seen.add(lineNumber + ":" + content));
    assertEquals(List.of("3:0041 ; X", "5:0042;Y"), seen);
  }

  @Test
  void testLazyRethrowsFailureOnEveryCallUntilLoadSucceeds() {
    final AtomicInteger calls = new AtomicInteger();
    final BundledUnicodeData.Lazy<String> lazy = new BundledUnicodeData.Lazy<>(() -> {
      if (calls.incrementAndGet() < 3) {
        throw new IllegalStateException("attempt " + calls.get());
      }
      return "loaded";
    });
    assertEquals("attempt 1", assertThrows(IllegalStateException.class, lazy::get).getMessage());
    assertEquals("attempt 2", assertThrows(IllegalStateException.class, lazy::get).getMessage());
    assertEquals("loaded", lazy.get());
    assertEquals("loaded", lazy.get());
    assertEquals(3, calls.get());
  }

  @Test
  void testLazyRejectsNullLoaderResultOnEveryCall() {
    final AtomicInteger calls = new AtomicInteger();
    final BundledUnicodeData.Lazy<String> lazy = new BundledUnicodeData.Lazy<>(() -> {
      calls.incrementAndGet();
      return null;
    });
    assertEquals("loader returned null",
        assertThrows(IllegalStateException.class, lazy::get).getMessage());
    assertThrows(IllegalStateException.class, lazy::get);
    assertEquals(2, calls.get());
  }
}
