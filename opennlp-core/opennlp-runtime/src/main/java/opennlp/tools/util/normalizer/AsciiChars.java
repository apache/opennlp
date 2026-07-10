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

/**
 * ASCII character helpers shared by the cursor-scan rewrites of the legacy normalizers, so the
 * definitions the former regexes agreed on cannot drift apart between classes.
 */
final class AsciiChars {

  /** The six characters the former regex {@code \s} class matched. */
  static final CodePointSet WHITESPACE =
      CodePointSet.ofRange(0x0009, 0x000D).union(CodePointSet.of(0x0020));

  private AsciiChars() {
  }

  static char toLower(char c) {
    return c >= 'A' && c <= 'Z' ? (char) (c + 0x20) : c;
  }

  static int toLower(int codePoint) {
    return codePoint >= 'A' && codePoint <= 'Z' ? codePoint + 0x20 : codePoint;
  }

  static boolean caseInsensitiveEquals(char a, char b) {
    return toLower(a) == toLower(b);
  }

  static boolean caseInsensitiveEquals(int a, int b) {
    return toLower(a) == toLower(b);
  }
}
