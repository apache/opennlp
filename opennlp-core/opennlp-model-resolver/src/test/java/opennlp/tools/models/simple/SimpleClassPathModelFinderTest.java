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

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.models.AbstractClassPathFinderTest;
import opennlp.tools.models.ClassPathModelFinder;

public class SimpleClassPathModelFinderTest extends AbstractClassPathFinderTest {


  @Override
  protected ClassPathModelFinder getModelFinder() {
    return new SimpleClassPathModelFinder();
  }

  @Override
  protected ClassPathModelFinder getModelFinder(String pattern) {
    return new SimpleClassPathModelFinder(pattern);
  }

  private static Stream<Arguments> unixClassPaths() {
    return Stream.of(
        Arguments.of("", new String[] {""}),
        Arguments.of(":", new String[0]),
        Arguments.of("::", new String[0]),
        Arguments.of("a.jar", new String[] {"a.jar"}),
        Arguments.of("a.jar:b.jar", new String[] {"a.jar", "b.jar"}),
        Arguments.of(":a.jar", new String[] {"", "a.jar"}),
        Arguments.of("a.jar:", new String[] {"a.jar"}),
        Arguments.of("a.jar::", new String[] {"a.jar"}),
        Arguments.of("a.jar::b.jar", new String[] {"a.jar", "", "b.jar"}),
        Arguments.of("::a.jar:", new String[] {"", "", "a.jar"}),
        Arguments.of("/usr/lib/a.jar:/opt/b.jar", new String[] {"/usr/lib/a.jar", "/opt/b.jar"}),
        Arguments.of("C:\\lib\\a.jar;C:\\lib\\b.jar", new String[] {"C", "\\lib\\a.jar;C", "\\lib\\b.jar"}),
        Arguments.of("\uD801\uDC12.jar:b.jar", new String[] {"\uD801\uDC12.jar", "b.jar"}));
  }

  @ParameterizedTest
  @MethodSource("unixClassPaths")
  void testSplitClassPathUnix(String classPath, String[] expected) {
    Assertions.assertArrayEquals(expected, SimpleClassPathModelFinder.splitClassPath(classPath, false));
    Assertions.assertArrayEquals(classPath.split(":"),
        SimpleClassPathModelFinder.splitClassPath(classPath, false));
  }

  private static Stream<Arguments> windowsClassPaths() {
    return Stream.of(
        Arguments.of("", new String[] {""}),
        Arguments.of(";", new String[0]),
        Arguments.of(";;", new String[0]),
        Arguments.of("a.jar", new String[] {"a.jar"}),
        Arguments.of("a.jar;b.jar", new String[] {"a.jar", "b.jar"}),
        Arguments.of(";a.jar", new String[] {"", "a.jar"}),
        Arguments.of("a.jar;", new String[] {"a.jar"}),
        Arguments.of("a.jar;;", new String[] {"a.jar"}),
        Arguments.of("a.jar;;b.jar", new String[] {"a.jar", "", "b.jar"}),
        Arguments.of(";;a.jar;", new String[] {"", "", "a.jar"}),
        Arguments.of("C:\\lib\\a.jar;C:\\lib\\b.jar", new String[] {"C:\\lib\\a.jar", "C:\\lib\\b.jar"}),
        Arguments.of("/usr/lib/a.jar:/opt/b.jar", new String[] {"/usr/lib/a.jar:/opt/b.jar"}),
        Arguments.of("\uD801\uDC12.jar;b.jar", new String[] {"\uD801\uDC12.jar", "b.jar"}));
  }

  @ParameterizedTest
  @MethodSource("windowsClassPaths")
  void testSplitClassPathWindows(String classPath, String[] expected) {
    Assertions.assertArrayEquals(expected, SimpleClassPathModelFinder.splitClassPath(classPath, true));
    Assertions.assertArrayEquals(classPath.split(";"),
        SimpleClassPathModelFinder.splitClassPath(classPath, true));
  }
}
