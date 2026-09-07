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

package opennlp.tools.tokenize.lang.en;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.Span;

/**
 * Class which produces an Iterator&lt;TokenSample&gt; from a file of space delimited token.
 * This class uses a number of English-specific heuristics to un-separate tokens which
 * are typically found together in text.
 */
public class TokenSampleStream implements Iterator<TokenSample> {

  private static final Logger logger = LoggerFactory.getLogger(TokenSampleStream.class);
  private final BufferedReader in;
  private String line;
  private boolean evenq = true;

  public TokenSampleStream(InputStream is) throws IOException {
    this.in = new BufferedReader(new InputStreamReader(is));
    line = in.readLine();
  }

  public boolean hasNext() {
    return line != null;
  }

  public TokenSample next() {
    String[] tokens = splitOnWhitespace(line);
    if (tokens.length == 0) {
      evenq = true;
    }
    StringBuilder sb = new StringBuilder(line.length());
    List<Span> spans = new ArrayList<>();
    int length = 0;
    for (int ti = 0; ti < tokens.length; ti++) {
      String token = tokens[ti];
      String lastToken = ti - 1 >= 0 ? tokens[ti - 1] : "";
      token = switch (token) {
        case "-LRB-" -> "(";
        case "-LCB-" -> "{";
        case "-RRB-" -> ")";
        case "-RCB-" -> "}";
        default -> token;
      };
      if (sb.length() != 0) {
        if (!containsAsciiAlphaNum(token) || token.startsWith("'") || token.equalsIgnoreCase("n't")) {
          if ((token.equals("``") || token.equals("--") || token.equals("$") ||
              token.equals("(")  || token.equals("&")  || token.equals("#") ||
              (token.equals("\"") && (evenq && ti != tokens.length - 1)))
              && (!lastToken.equals("(") || !lastToken.equals("{"))) {
            length++;
          }
        }
        else {
          if (!lastToken.equals("``") && (!lastToken.equals("\"") || evenq) && !lastToken.equals("(")
              && !lastToken.equals("{") && !lastToken.equals("$") && !lastToken.equals("#")) {
            length++;
          }
        }
      }
      if (token.equals("\"")) {
        evenq = ti == tokens.length - 1 || !evenq;
      }
      if (sb.length() < length) {
        sb.append(" ");
      }
      sb.append(token);
      spans.add(new Span(length, length + token.length()));
      length += token.length();
    }

    try {
      line = in.readLine();
    } catch (IOException e) {
      logger.error(e.getLocalizedMessage(), e);
      line = null;
    }
    return new TokenSample(sb.toString(),spans.toArray(new Span[0]));
  }


  public void remove() {
    throw new UnsupportedOperationException();
  }

  /**
   * Splits on ASCII whitespace runs like {@code String.split("\\s+")}: a leading run gives one
   * empty field, runs collapse, trailing empty fields are dropped.
   *
   * @param line The line.
   * @return The fields. Never {@code null}.
   */
  private static String[] splitOnWhitespace(String line) {
    if (line.isEmpty()) {
      return new String[] {""};
    }
    List<String> tokens = new ArrayList<>();
    if (isAsciiWhitespace(line.charAt(0))) {
      tokens.add("");
    }
    int start = 0;
    for (int i = 0; i < line.length(); i++) {
      if (isAsciiWhitespace(line.charAt(i))) {
        if (i > start) {
          tokens.add(line.substring(start, i));
        }
        while (i + 1 < line.length() && isAsciiWhitespace(line.charAt(i + 1))) {
          i++;
        }
        start = i + 1;
      }
    }
    if (line.length() > start) {
      tokens.add(line.substring(start));
    }
    return tokens.toArray(new String[0]);
  }

  /**
   * Tests for ASCII whitespace: space, tab, line feed, vertical tab, form feed, carriage return.
   *
   * @param c The character.
   * @return {@code true} for one of those six characters.
   */
  private static boolean isAsciiWhitespace(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r';
  }

  /**
   * Tests whether a token contains an ASCII letter or digit.
   *
   * @param token The token.
   * @return {@code true} if one is present.
   */
  private static boolean containsAsciiAlphaNum(String token) {
    for (int i = 0; i < token.length(); i++) {
      char c = token.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
        return true;
      }
    }
    return false;
  }

  private static void usage() {
    logger.info("TokenSampleStream [-spans] < in");
    logger.info("Where in is a space delimited list of tokens.");
  }
}
