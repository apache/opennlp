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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.util.Span;

/**
 * Demonstrates the intended end-to-end usage of this package with miniature,
 * project-authored data: a mecab-format dictionary archive is installed with
 * {@link MecabDictionaryInstaller}, loaded as a {@link MecabDictionary}, and segmented
 * with a {@link LatticeTokenizer}; a plain frequency lexicon is loaded and segmented
 * with a {@link UnigramSegmenter}. Everything is written to a temporary directory by
 * the test itself; no external dictionary or lexicon data and no network access are
 * involved.
 *
 * <p>Source strings are written as Unicode escapes to keep this file ASCII-only. The
 * Japanese fixture words are Tokyo (U+6771 U+4EAC), Kyoto (U+4EAC U+90FD), east
 * (U+6771), the metropolis suffix (U+90FD), the case particle ni (U+306B), and the
 * verb iku, to go (U+884C U+304F); the Chinese fixture words are wo, I (U+6211),
 * laidao, arrive (U+6765 U+5230), Beijing (U+5317 U+4EAC), and Tiananmen
 * (U+5929 U+5B89 U+95E8).</p>
 */
public class LatticeUsageExampleTest {

  /**
   * Walks the full mecab-format flow: package a miniature Japanese dictionary as a
   * {@code tar.gz} archive, install it from a file URI, load it, and tokenize. The
   * segmentation must pick the cheaper path (Tokyo plus the metropolis suffix) over
   * the competing reading (east plus Kyoto), the spans must be in original text
   * coordinates, and the morphemes must carry the dictionary's feature columns.
   */
  @Test
  void testInstallLoadAndTokenizeAMecabFormatDictionary(@TempDir Path work)
      throws IOException {
    // A minimal but complete dictionary: one lexicon file plus the three definition
    // files every mecab-format distribution contains, wrapped like a release archive.
    final byte[] archive = TarGzArchives.gzippedTar(new String[][] {
        {"mini-dict-0.1/lexicon.csv", String.join("\n",
            "\u6771\u4EAC,0,0,3000,noun,proper",
            "\u4EAC\u90FD,0,0,3000,noun,proper",
            "\u6771,0,0,6000,noun,common",
            "\u90FD,0,0,4000,noun,suffix",
            "\u306B,0,0,1000,particle,case",
            "\u884C\u304F,0,0,3000,verb,base",
            "")},
        {"mini-dict-0.1/matrix.def", "1 1\n0 0 0\n"},
        {"mini-dict-0.1/char.def", String.join("\n",
            "DEFAULT 0 1 0",
            "KANJI 0 0 2",
            "HIRAGANA 0 1 0",
            "",
            "0x3041..0x3096 HIRAGANA",
            "0x4E00..0x9FFF KANJI",
            "")},
        {"mini-dict-0.1/unk.def", String.join("\n",
            "DEFAULT,0,0,10000,symbol,unknown",
            "KANJI,0,0,8000,noun,unknown",
            "HIRAGANA,0,0,9000,particle,unknown",
            "")},
        {"mini-dict-0.1/README", "not a dictionary payload file"}});
    final Path archiveFile = work.resolve("mini-dict-0.1.tar.gz");
    Files.write(archiveFile, archive);

    // Install: fetch the archive from the user-chosen location and unpack the payload.
    final Path dictionaryDirectory = work.resolve("dictionary");
    final int extracted =
        MecabDictionaryInstaller.install(archiveFile.toUri(), dictionaryDirectory);
    Assertions.assertEquals(4, extracted);

    // Load and tokenize; both views must agree and stay in original coordinates.
    final LatticeTokenizer tokenizer =
        new LatticeTokenizer(MecabDictionary.load(dictionaryDirectory));
    final String text = "\u6771\u4EAC\u90FD\u306B\u884C\u304F";
    Assertions.assertArrayEquals(
        new String[] {"\u6771\u4EAC", "\u90FD", "\u306B", "\u884C\u304F"},
        tokenizer.tokenize(text));
    Assertions.assertArrayEquals(new Span[] {
        new Span(0, 2), new Span(2, 3), new Span(3, 4), new Span(4, 6)},
        tokenizer.tokenizePos(text));

    // The analyze view adds the dictionary's feature columns to every morpheme.
    final List<Morpheme> morphemes = tokenizer.analyze(text);
    Assertions.assertEquals(4, morphemes.size());
    Assertions.assertEquals("\u6771\u4EAC", morphemes.get(0).surface());
    Assertions.assertEquals(List.of("noun", "proper"), morphemes.get(0).features());
    Assertions.assertFalse(morphemes.get(0).unknown());
  }

  /**
   * Walks the frequency-lexicon flow: write a miniature word-count lexicon to a file,
   * load it, and segment. The segmentation must recover the listed multi-character
   * words with spans in original text coordinates.
   */
  @Test
  void testLoadAndSegmentWithAFrequencyLexicon(@TempDir Path work) throws IOException {
    // One word, its count, and an optional tag per line, whitespace separated.
    final Path lexicon = work.resolve("words.txt");
    Files.write(lexicon, String.join("\n",
        "\u6211 5000 r",
        "\u6765\u5230 2000 v",
        "\u5317\u4EAC 3000 ns",
        "\u5929\u5B89\u95E8 1200 ns",
        "").getBytes(StandardCharsets.UTF_8));

    final UnigramSegmenter segmenter = UnigramSegmenter.load(lexicon);
    final String text = "\u6211\u6765\u5230\u5317\u4EAC\u5929\u5B89\u95E8";
    Assertions.assertArrayEquals(
        new String[] {"\u6211", "\u6765\u5230", "\u5317\u4EAC", "\u5929\u5B89\u95E8"},
        segmenter.tokenize(text));
    Assertions.assertArrayEquals(new Span[] {
        new Span(0, 1), new Span(1, 3), new Span(3, 5), new Span(5, 8)},
        segmenter.tokenizePos(text));
  }

  /**
   * Walks the non-UTF-8 flow that widely used Japanese distributions require: the same
   * miniature dictionary is written to disk encoded in EUC-JP and loaded through the
   * charset-taking overload. The segmentation must match the UTF-8 run exactly, which
   * shows the encoding is a property of loading, not of tokenization.
   */
  @Test
  void testLoadAnEucJpEncodedDictionary(@TempDir Path work) throws IOException {
    final Charset eucJp = Charset.forName("EUC-JP");
    Files.write(work.resolve("lexicon.csv"), String.join("\n",
        "\u6771\u4EAC,0,0,3000,noun,proper",
        "\u4EAC\u90FD,0,0,3000,noun,proper",
        "\u6771,0,0,6000,noun,common",
        "\u90FD,0,0,4000,noun,suffix",
        "\u306B,0,0,1000,particle,case",
        "\u884C\u304F,0,0,3000,verb,base",
        "").getBytes(eucJp));
    Files.write(work.resolve("matrix.def"), "1 1\n0 0 0\n".getBytes(eucJp));
    Files.write(work.resolve("char.def"), String.join("\n",
        "DEFAULT 0 1 0",
        "KANJI 0 0 2",
        "HIRAGANA 0 1 0",
        "",
        "0x3041..0x3096 HIRAGANA",
        "0x4E00..0x9FFF KANJI",
        "").getBytes(eucJp));
    Files.write(work.resolve("unk.def"), String.join("\n",
        "DEFAULT,0,0,10000,symbol,unknown",
        "KANJI,0,0,8000,noun,unknown",
        "HIRAGANA,0,0,9000,particle,unknown",
        "").getBytes(eucJp));

    final LatticeTokenizer tokenizer =
        new LatticeTokenizer(MecabDictionary.load(work, eucJp));
    Assertions.assertArrayEquals(
        new String[] {"\u6771\u4EAC", "\u90FD", "\u306B", "\u884C\u304F"},
        tokenizer.tokenize("\u6771\u4EAC\u90FD\u306B\u884C\u304F"));
  }
}
