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

package opennlp.embeddings.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import opennlp.tools.util.StringUtil;

/**
 * Parses the hand-keyed HTML transcription of Bouvier's Law Dictionary (6th revised
 * edition, 1856) into {@link DictionaryEntry dictionary entries}.
 *
 * <p>An entry is a paragraph whose first bold run is the headword; paragraphs without a
 * leading bold run continue the previous entry's definition (the numbered clauses of the
 * original). Headwords keep their original upper-case form; trailing punctuation is
 * stripped and page furniture (titles, navigation) is filtered out. The first occurrence
 * of a headword wins; later duplicates are dropped.</p>
 *
 * @since 3.0.0
 */
public final class BouvierDictionaryParser {

  private static final int MIN_DEFINITION_LENGTH = 20;
  private static final int MAX_HEADWORD_LENGTH = 60;

  /** Prevents construction of this utility class. */
  private BouvierDictionaryParser() {
  }

  /**
   * Parses every {@code *.htm} file of a raw Bouvier directory, in file name order.
   *
   * @param directory The directory holding {@code bouvier_a.htm} through
   *                  {@code bouvier_z.htm}. Must not be {@code null}.
   * @return The entries of all files, first occurrence of each headword only. Never
   *         {@code null}.
   * @throws IOException Thrown if reading fails or the directory holds no {@code .htm}
   *         file.
   * @throws IllegalArgumentException Thrown if {@code directory} is {@code null}.
   */
  public static List<DictionaryEntry> parseDirectory(Path directory) throws IOException {
    if (directory == null) {
      throw new IllegalArgumentException("directory must not be null");
    }
    final List<Path> files = new ArrayList<>();
    try (DirectoryStream<Path> listing = Files.newDirectoryStream(directory, "*.htm")) {
      listing.forEach(files::add);
    }
    if (files.isEmpty()) {
      throw new IOException("No .htm files in " + directory);
    }
    files.sort(null);
    final List<DictionaryEntry> entries = new ArrayList<>();
    final Set<String> seen = new HashSet<>();
    for (Path file : files) {
      // The archived HTML files use ISO-8859-1.
      for (DictionaryEntry entry : parse(Files.readString(file, StandardCharsets.ISO_8859_1))) {
        if (seen.add(StringUtil.toLowerCase(entry.headword()))) {
          entries.add(entry);
        }
      }
    }
    return entries;
  }

  /**
   * Parses one per-letter HTML document.
   *
   * @param html The document text. Must not be {@code null}.
   * @return The entries in document order, duplicates included. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code html} is {@code null}.
   */
  public static List<DictionaryEntry> parse(String html) {
    if (html == null) {
      throw new IllegalArgumentException("html must not be null");
    }
    final List<String[]> raw = new ArrayList<>();
    String paragraphBold = null;
    final StringBuilder paragraphText = new StringBuilder();
    boolean inParagraph = false;
    boolean inBold = false;
    boolean sawTextBeforeBold = false;

    int i = 0;
    final int length = html.length();
    while (i < length) {
      final char c = html.charAt(i);
      if (c == '<') {
        final int close = html.indexOf('>', i + 1);
        if (close < 0) {
          break;
        }
        final String tag = StringUtil.toLowerCase(html.substring(i + 1, close).trim());
        final String name = tagName(tag);
        switch (name) {
          case "p" -> {
            flushParagraph(raw, paragraphBold, paragraphText);
            paragraphBold = null;
            paragraphText.setLength(0);
            sawTextBeforeBold = false;
            inParagraph = true;
          }
          case "/p" -> {
            flushParagraph(raw, paragraphBold, paragraphText);
            paragraphBold = null;
            paragraphText.setLength(0);
            sawTextBeforeBold = false;
            inParagraph = false;
          }
          case "b" -> inBold = inParagraph;
          case "/b" -> inBold = false;
          default -> {
            // Other elements do not change paragraph or bold state.
          }
        }
        i = close + 1;
        continue;
      }
      final int nextTag = html.indexOf('<', i);
      final String data = decodeEntities(
          html.substring(i, nextTag < 0 ? length : nextTag));
      if (inParagraph && !data.isBlank()) {
        if (inBold && paragraphBold == null && !sawTextBeforeBold) {
          paragraphBold = data.strip();
        } else {
          if (paragraphBold == null) {
            sawTextBeforeBold = true;
          }
          paragraphText.append(data).append(' ');
        }
      }
      i = nextTag < 0 ? length : nextTag;
    }
    flushParagraph(raw, paragraphBold, paragraphText);

    final List<DictionaryEntry> entries = new ArrayList<>(raw.size());
    for (String[] pair : raw) {
      final String headword = cleanHeadword(pair[0]);
      final String definition = pair[1];
      if (!headword.isEmpty() && definition.length() >= MIN_DEFINITION_LENGTH) {
        entries.add(new DictionaryEntry(headword, definition));
      }
    }
    return entries;
  }

  /**
   * Returns the element name without its attributes.
   *
   * @param tag The lower-case tag contents without angle brackets.
   * @return The element name.
   */
  private static String tagName(String tag) {
    for (int i = 0; i < tag.length(); i++) {
      if (Character.isWhitespace(tag.charAt(i))) {
        return tag.substring(0, i);
      }
    }
    return tag;
  }

  /**
   * Appends the finished paragraph: a new entry when it opened with a plausible bold
   * headword, otherwise a continuation of the previous entry's definition.
   *
   * @param raw The parsed headword and definition pairs.
   * @param bold The leading bold text, or {@code null}.
   * @param text The remaining paragraph text.
   */
  private static void flushParagraph(List<String[]> raw, String bold, StringBuilder text) {
    String body = collapseWhitespace(text.toString());
    while (!body.isEmpty() && isLeadingPunctuation(body.charAt(0))) {
      body = body.substring(1).strip();
    }
    if (bold != null && isPlausibleHeadword(bold)) {
      raw.add(new String[] {bold, body});
    } else if (!raw.isEmpty() && !body.isEmpty()) {
      final String[] last = raw.get(raw.size() - 1);
      last[1] = (last[1] + " " + body).strip();
    }
  }

  /**
   * Tests whether a character may separate a headword from its definition.
   *
   * @param c The character.
   * @return {@code true} for recognized leading punctuation.
   */
  private static boolean isLeadingPunctuation(char c) {
    return c == ',' || c == '.' || c == ';' || c == ':' || c == '-';
  }

  /**
   * Tests whether bold text has the form of a dictionary headword.
   *
   * @param word The bold text.
   * @return {@code true} when the text can be a headword.
   */
  private static boolean isPlausibleHeadword(String word) {
    final String candidate = word.strip();
    if (candidate.isEmpty() || candidate.length() > MAX_HEADWORD_LENGTH) {
      return false;
    }
    if (!candidate.equals(StringUtil.toUpperCase(candidate))) {
      return false;
    }
    if (candidate.contains("BOUVIER") || candidate.contains("DICTIONARY")) {
      return false;
    }
    for (int i = 0; i < candidate.length(); i++) {
      final char c = candidate.charAt(i);
      if (!Character.isLetterOrDigit(c) && " '.,&-".indexOf(c) < 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * {@return the text with every whitespace run collapsed to a single space and both ends
   * trimmed}
   *
   * @param text The text to collapse.
   */
  private static String collapseWhitespace(String text) {
    final StringBuilder collapsed = new StringBuilder(text.length());
    final int length = text.length();
    boolean pendingSpace = false;
    int i = 0;
    while (i < length) {
      final int c = text.codePointAt(i);
      if (Character.isWhitespace(c)) {
        pendingSpace = collapsed.length() > 0;
      } else {
        if (pendingSpace) {
          collapsed.append(' ');
          pendingSpace = false;
        }
        collapsed.appendCodePoint(c);
      }
      i += Character.charCount(c);
    }
    return collapsed.toString();
  }

  /**
   * Removes trailing punctuation and collapses whitespace in a headword.
   *
   * @param word The source headword.
   * @return The cleaned headword.
   */
  private static String cleanHeadword(String word) {
    String cleaned = collapseWhitespace(word);
    while (!cleaned.isEmpty()
        && " ,.;:".indexOf(cleaned.charAt(cleaned.length() - 1)) >= 0) {
      cleaned = cleaned.substring(0, cleaned.length() - 1);
    }
    return cleaned;
  }

  /**
   * Decodes supported entity references in text content.
   *
   * @param data The text content.
   * @return The decoded text.
   */
  private static String decodeEntities(String data) {
    if (data.indexOf('&') < 0) {
      return data;
    }
    final StringBuilder out = new StringBuilder(data.length());
    int i = 0;
    while (i < data.length()) {
      final char c = data.charAt(i);
      if (c != '&') {
        out.append(c);
        i++;
        continue;
      }
      final int semicolon = data.indexOf(';', i + 1);
      final String decoded = semicolon > i && semicolon - i <= 9
          ? decodeEntity(data.substring(i + 1, semicolon)) : null;
      if (decoded == null) {
        out.append(c);
        i++;
      } else {
        out.append(decoded);
        i = semicolon + 1;
      }
    }
    return out.toString();
  }

  /**
   * Decodes one entity reference: the named entities the transcription uses, then the
   * decimal and hexadecimal numeric forms. Unknown references decode to {@code null}
   * and pass through literally.
   *
   * @param name The reference without {@code &} and {@code ;}.
   * @return The decoded text, or {@code null} for an unsupported reference.
   */
  private static String decodeEntity(String name) {
    final String named = switch (name) {
      case "amp" -> "&";
      case "lt" -> "<";
      case "gt" -> ">";
      case "quot" -> "\"";
      case "nbsp" -> " ";
      case "sect" -> "§";
      case "cedil" -> "¸";
      case "fnof" -> "ƒ";
      case "ge" -> "≥";
      case "laquo" -> "«";
      case "yen" -> "¥";
      case "radic" -> "√";
      case "iquest" -> "¿";
      case "acute" -> "´";
      case "reg" -> "®";
      case "uml" -> "¨";
      case "deg" -> "°";
      case "macr" -> "¯";
      case "iexcl" -> "¡";
      case "auml" -> "ä";
      case "uuml" -> "ü";
      case "ouml" -> "ö";
      case "Auml" -> "Ä";
      case "Uuml" -> "Ü";
      case "Ouml" -> "Ö";
      case "ntilde" -> "ñ";
      case "Ntilde" -> "Ñ";
      case "eacute" -> "é";
      case "Eacute" -> "É";
      case "egrave" -> "è";
      case "Egrave" -> "È";
      case "agrave" -> "à";
      case "Aacute" -> "Á";
      case "aacute" -> "á";
      case "uacute" -> "ú";
      case "ugrave" -> "ù";
      case "yacute" -> "ý";
      case "Yacute" -> "Ý";
      case "ccedil" -> "ç";
      case "Ccedil" -> "Ç";
      case "oslash" -> "ø";
      case "Oslash" -> "Ø";
      case "ocirc" -> "ô";
      case "igrave" -> "ì";
      case "iuml" -> "ï";
      default -> null;
    };
    if (named != null) {
      return named;
    }
    if (name.isEmpty() || name.charAt(0) != '#') {
      return null;
    }
    final int code;
    try {
      code = name.length() > 1 && (name.charAt(1) == 'x' || name.charAt(1) == 'X')
          ? Integer.parseInt(name.substring(2), 16)
          : Integer.parseInt(name.substring(1));
    } catch (NumberFormatException e) {
      return null;
    }
    if (code <= 0 || code > Character.MAX_CODE_POINT
        || code >= Character.MIN_SURROGATE && code <= Character.MAX_SURROGATE) {
      return null;
    }
    return new String(Character.toChars(windows1252Remap(code)));
  }

  /**
   * Maps the C1 control range through Windows-1252, the HTML rule for numeric
   * references such as the transcription's {@code &#150;} en dashes.
   *
   * @param code The numeric reference value.
   * @return The mapped Unicode code point.
   */
  private static int windows1252Remap(int code) {
    return switch (code) {
      case 0x80 -> 0x20AC;
      case 0x82 -> 0x201A;
      case 0x83 -> 0x0192;
      case 0x84 -> 0x201E;
      case 0x85 -> 0x2026;
      case 0x86 -> 0x2020;
      case 0x87 -> 0x2021;
      case 0x88 -> 0x02C6;
      case 0x89 -> 0x2030;
      case 0x8A -> 0x0160;
      case 0x8B -> 0x2039;
      case 0x8C -> 0x0152;
      case 0x8E -> 0x017D;
      case 0x91 -> 0x2018;
      case 0x92 -> 0x2019;
      case 0x93 -> 0x201C;
      case 0x94 -> 0x201D;
      case 0x95 -> 0x2022;
      case 0x96 -> 0x2013;
      case 0x97 -> 0x2014;
      case 0x98 -> 0x02DC;
      case 0x99 -> 0x2122;
      case 0x9A -> 0x0161;
      case 0x9B -> 0x203A;
      case 0x9C -> 0x0153;
      case 0x9E -> 0x017E;
      case 0x9F -> 0x0178;
      default -> code;
    };
  }
}
