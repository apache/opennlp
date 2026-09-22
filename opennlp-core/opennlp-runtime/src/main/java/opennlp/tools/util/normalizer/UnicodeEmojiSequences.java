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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Exact Unicode 18 fully-qualified emoji sequence matcher. */
final class UnicodeEmojiSequences {
  private static final String RESOURCE =
      "/opennlp/tools/util/normalizer/emoji/EmojiSequences-18.0.txt";
  private static final UnicodeEmojiSequences INSTANCE = load();

  private final Node root;
  private final int[][] componentRanges;

  private UnicodeEmojiSequences(Node root, int[][] componentRanges) {
    this.root = root;
    this.componentRanges = componentRanges;
  }

  static UnicodeEmojiSequences getInstance() {
    return INSTANCE;
  }

  Candidate candidateAt(CharSequence text, int start) {
    int end = match(text, start);
    boolean valid = end >= 0;
    if (!valid) {
      int first = Character.codePointAt(text, start);
      int prefixEnd = prefixEnd(text, start);
      if (!isKeycapBase(first) && prefixEnd > start && prefixEnd < text.length()
          && isStructuralComponent(Character.codePointAt(text, prefixEnd))) {
        end = prefixEnd;
      }
      else if (isStructuralComponent(first)) {
        end = start + Character.charCount(first);
      }
      else {
        return null;
      }
    }
    int position = end;
    while (position < text.length()) {
      int next = match(text, position);
      if (next >= 0) {
        position = next;
        continue;
      }
      int codePoint = Character.codePointAt(text, position);
      if (!isStructuralComponent(codePoint)) {
        break;
      }
      valid = false;
      position += Character.charCount(codePoint);
    }
    return new Candidate(position, valid);
  }

  private int match(CharSequence text, int start) {
    Node node = root;
    int longest = -1;
    for (int i = start; i < text.length();) {
      int codePoint = Character.codePointAt(text, i);
      node = node.children.get(codePoint);
      if (node == null) {
        break;
      }
      i += Character.charCount(codePoint);
      if (node.terminal) {
        longest = i;
      }
    }
    return longest;
  }

  private int prefixEnd(CharSequence text, int start) {
    Node node = root;
    int end = start;
    for (int i = start; i < text.length();) {
      int codePoint = Character.codePointAt(text, i);
      node = node.children.get(codePoint);
      if (node == null) {
        break;
      }
      i += Character.charCount(codePoint);
      end = i;
    }
    return end;
  }

  private boolean isStructuralComponent(int codePoint) {
    if (isKeycapBase(codePoint)) {
      return false;
    }
    if (codePoint == 0xFE0E) {
      return true;
    }
    int low = 0;
    int high = componentRanges.length - 1;
    while (low <= high) {
      int middle = (low + high) >>> 1;
      int[] range = componentRanges[middle];
      if (codePoint < range[0]) {
        high = middle - 1;
      }
      else if (codePoint > range[1]) {
        low = middle + 1;
      }
      else {
        return true;
      }
    }
    return false;
  }

  private boolean isKeycapBase(int codePoint) {
    return codePoint == '#' || codePoint == '*' || codePoint >= '0' && codePoint <= '9';
  }

  private static UnicodeEmojiSequences load() {
    Node root = new Node();
    List<int[]> ranges = new ArrayList<>();
    try (InputStream stream = UnicodeEmojiSequences.class.getResourceAsStream(RESOURCE)) {
      if (stream == null) {
        throw new IOException("missing resource " + RESOURCE);
      }
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(stream, StandardCharsets.US_ASCII))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.startsWith("S;")) {
            addSequence(root, line, 2);
          }
          else if (line.startsWith("C;")) {
            ranges.add(parseRange(line.substring(2)));
          }
        }
      }
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
    return new UnicodeEmojiSequences(root, ranges.toArray(int[][]::new));
  }

  private static void addSequence(Node root, String line, int offset) throws IOException {
    Node node = root;
    int tokenStart = offset;
    for (int i = offset; i <= line.length(); i++) {
      if (i == line.length() || line.charAt(i) == ' ') {
        int codePoint = parseHex(line, tokenStart, i);
        node = node.children.computeIfAbsent(codePoint, ignored -> new Node());
        tokenStart = i + 1;
      }
    }
    node.terminal = true;
  }

  private static int[] parseRange(String value) throws IOException {
    int dots = value.indexOf("..");
    if (dots < 0) {
      int codePoint = parseHex(value, 0, value.length());
      return new int[] {codePoint, codePoint};
    }
    return new int[] {parseHex(value, 0, dots), parseHex(value, dots + 2, value.length())};
  }

  private static int parseHex(String value, int start, int end) throws IOException {
    try {
      return Integer.parseInt(value, start, end, 16);
    } catch (NumberFormatException e) {
      throw new IOException("invalid emoji data: " + value, e);
    }
  }

  record Candidate(int end, boolean valid) {
  }

  private static final class Node {
    private final Map<Integer, Node> children = new HashMap<>();
    private boolean terminal;
  }
}
