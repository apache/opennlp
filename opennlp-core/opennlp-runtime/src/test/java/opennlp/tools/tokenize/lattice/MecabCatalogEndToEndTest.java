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

package opennlp.tools.tokenize.lattice;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Downloads the pinned catalog dictionaries and runs them through the full
 * pipeline: digest-verified fetch, archive extraction, {@link MecabDictionary}
 * load, and {@link LatticeTokenizer} segmentation. Verifies the two catalog
 * distributions end to end: IPADIC 2.7.0 (EUC-JP, Japanese) and mecab-ko-dic
 * 2.1.1 (UTF-8, Korean).
 *
 * <p>Skipped unless {@code -Dopennlp.download.remote=true} is set, matching
 * the gate on {@link opennlp.tools.util.DictionaryCatalog}.</p>
 */
public class MecabCatalogEndToEndTest {

  @TempDir
  private static Path workDir;

  @BeforeAll
  static void requireRemoteOptIn() {
    Assumptions.assumeTrue(Boolean.getBoolean("opennlp.download.remote"),
        "remote downloads are opt-in via -Dopennlp.download.remote=true");
  }

  @Test
  void testIpadicInstallsLoadsAndSegments() throws Exception {
    MecabDictionary dict = installAndLoad("mecab.ipadic", Charset.forName("EUC-JP"));
    LatticeTokenizer tokenizer = new LatticeTokenizer(dict);

    String text = "すもももももももものうち";
    List<Morpheme> morphemes = tokenizer.analyze(text);
    assertFalse(morphemes.isEmpty());
    assertEquals(List.of("すもも", "も", "もも", "も", "もも", "の", "うち"),
        morphemes.stream().map(Morpheme::surface).toList());
    assertCoversText(tokenizer.tokenizePos(text), text);
  }

  @Test
  void testKoDicInstallsLoadsAndSegments() throws Exception {
    MecabDictionary dict = installAndLoad("mecab.ko-dic", StandardCharsets.UTF_8);
    LatticeTokenizer tokenizer = new LatticeTokenizer(dict);

    String text = "아버지가 방에 들어가신다";
    List<Morpheme> morphemes = tokenizer.analyze(text);
    assertFalse(morphemes.isEmpty());
    assertCoversText(tokenizer.tokenizePos(text), text);
  }

  /**
   * Installs the given catalog dictionary into a fresh directory and loads it.
   *
   * @param dictionaryId The catalog identifier, for example {@code mecab.ipadic}.
   *                     Must not be null.
   * @param charset The encoding the distribution uses. Must not be null.
   * @return The loaded dictionary.
   */
  private static MecabDictionary installAndLoad(String dictionaryId, Charset charset)
      throws Exception {
    Path dir = workDir.resolve(dictionaryId);
    Files.createDirectories(dir);
    int installed = MecabDictionaryInstaller.installFromCatalog(dictionaryId, dir);
    assertTrue(installed > 0, "no files extracted for " + dictionaryId);

    try (Stream<Path> paths = Files.walk(dir, 3)) {
      Path root = paths.filter(p -> p.getFileName().toString().equals("matrix.def"))
          .map(Path::getParent)
          .findFirst()
          .orElseThrow();
      return MecabDictionary.load(root, charset);
    }
  }

  /**
   * Asserts the spans are in order, non-overlapping, and each maps to non-blank text.
   *
   * @param spans The token spans to check. Must not be null.
   * @param text The tokenized text. Must not be null.
   */
  private static void assertCoversText(Span[] spans, String text) {
    assertTrue(spans.length > 0);
    int last = 0;
    for (Span span : spans) {
      assertTrue(span.getStart() >= last, "spans overlap or regress");
      assertFalse(text.substring(span.getStart(), span.getEnd()).isBlank());
      last = span.getEnd();
    }
  }
}
