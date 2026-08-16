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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import opennlp.tools.util.InvalidFormatException;

/**
 * Reads a Caselaw Access Project volume zip ({@code https://static.case.law/us/<N>.zip})
 * into {@link CasePassage passages}.
 *
 * <p>Each {@code json/*.json} entry is one case; each of its opinions is split on
 * newline paragraphs and packed into passages of roughly {@link #TARGET_CHARS}
 * characters without splitting inside a paragraph, so a passage stays a coherent run of
 * argument. A paragraph longer than {@link #HARD_MAX_CHARS} is cut at the last space
 * before the limit.</p>
 *
 * @since 3.0.0
 */
public final class CapVolumeReader {

  /** The soft passage size: a passage closes once it would grow past this. */
  public static final int TARGET_CHARS = 1200;

  /** The hard cut for a single paragraph longer than any passage should be. */
  public static final int HARD_MAX_CHARS = 2400;

  private CapVolumeReader() {
  }

  /**
   * Reads every case of one volume zip.
   *
   * @param zip The volume zip file. Its file name without extension is recorded as the
   *            volume. Must not be {@code null}.
   * @return The passages of all opinions, in case and opinion order. Never {@code null}.
   * @throws IOException Thrown if the zip cannot be read.
   * @throws InvalidFormatException Thrown if a case entry is not the expected JSON
   *         shape.
   * @throws IllegalArgumentException Thrown if {@code zip} is {@code null}.
   */
  public static List<CasePassage> read(Path zip) throws IOException, InvalidFormatException {
    if (zip == null) {
      throw new IllegalArgumentException("zip must not be null");
    }
    final String fileName = zip.getFileName().toString();
    final String volume = fileName.endsWith(".zip")
        ? fileName.substring(0, fileName.length() - 4) : fileName;
    final List<CasePassage> passages = new ArrayList<>();
    try (ZipFile file = new ZipFile(zip.toFile())) {
      final TreeSet<String> names = new TreeSet<>();
      file.stream().map(ZipEntry::getName)
          .filter(n -> n.startsWith("json/") && n.endsWith(".json"))
          .forEach(names::add);
      for (String name : names) {
        final String json = new String(
            file.getInputStream(file.getEntry(name)).readAllBytes(),
            StandardCharsets.UTF_8);
        readCase(json, zip + "!" + name, volume, passages);
      }
    }
    return passages;
  }

  private static void readCase(String json, String inputName, String volume,
      List<CasePassage> passages) throws InvalidFormatException {
    if (!(Json.parse(json, inputName) instanceof Map<?, ?> caseObject)) {
      throw new InvalidFormatException("Expected a JSON object in " + inputName);
    }
    final Object id = caseObject.get("id");
    if (id == null) {
      throw new InvalidFormatException("Missing case id in " + inputName);
    }
    final String caseName = caseObject.get("name_abbreviation") instanceof String s ? s : "";
    final String date = caseObject.get("decision_date") instanceof String s ? s : "";
    final String cite = officialCite(caseObject.get("citations"));
    if (!(caseObject.get("casebody") instanceof Map<?, ?> body)
        || !(body.get("opinions") instanceof List<?> opinions)) {
      throw new InvalidFormatException("Missing casebody.opinions in " + inputName);
    }
    for (int opinionIndex = 0; opinionIndex < opinions.size(); opinionIndex++) {
      if (!(opinions.get(opinionIndex) instanceof Map<?, ?> opinion)
          || !(opinion.get("text") instanceof String text)) {
        throw new InvalidFormatException(
            "Opinion " + opinionIndex + " without text in " + inputName);
      }
      final List<String> chunks = passagesOf(text);
      for (int seq = 0; seq < chunks.size(); seq++) {
        passages.add(new CasePassage(id + "-" + opinionIndex + "-" + seq,
            caseName, cite, date, volume, chunks.get(seq)));
      }
    }
  }

  private static String officialCite(Object citations) {
    if (!(citations instanceof List<?> list)) {
      return "";
    }
    String first = "";
    for (Object entry : list) {
      if (entry instanceof Map<?, ?> citation
          && citation.get("cite") instanceof String cite) {
        if ("official".equals(citation.get("type"))) {
          return cite;
        }
        if (first.isEmpty()) {
          first = cite;
        }
      }
    }
    return first;
  }

  /**
   * Packs newline paragraphs into passages: whole paragraphs up to the soft target,
   * over-long paragraphs cut at the last space before the hard maximum.
   */
  static List<String> passagesOf(String text) {
    final List<String> result = new ArrayList<>();
    final List<String> batch = new ArrayList<>();
    int size = 0;
    for (String rawParagraph : text.split("\n")) {
      String paragraph = rawParagraph.strip();
      if (paragraph.isEmpty()) {
        continue;
      }
      if (!batch.isEmpty() && size + paragraph.length() > TARGET_CHARS) {
        result.add(String.join(" ", batch));
        batch.clear();
        size = 0;
      }
      while (paragraph.length() > HARD_MAX_CHARS) {
        // The bound is exclusive: the cut lands strictly before the hard maximum.
        int cut = paragraph.lastIndexOf(' ', HARD_MAX_CHARS - 1);
        if (cut <= 0) {
          cut = HARD_MAX_CHARS;
        }
        result.add(paragraph.substring(0, cut));
        paragraph = paragraph.substring(cut).strip();
      }
      if (!paragraph.isEmpty()) {
        batch.add(paragraph);
        size += paragraph.length() + 1;
      }
    }
    if (!batch.isEmpty()) {
      result.add(String.join(" ", batch));
    }
    return result;
  }
}
