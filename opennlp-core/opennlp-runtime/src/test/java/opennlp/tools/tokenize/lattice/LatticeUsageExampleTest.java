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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

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
 */
public class LatticeUsageExampleTest {

  /**
   * Appends one ustar file entry to a growing tar image: a 512-byte header block
   * followed by the content padded to a block boundary.
   *
   * @param tar The tar image under construction. Must not be {@code null}.
   * @param name The entry name including any directory prefix. Must not be
   *             {@code null}, empty, or longer than the 100-byte header name field.
   * @param content The entry content bytes. Must not be {@code null}.
   * @throws IOException Thrown if writing to the in-memory stream fails.
   * @throws IllegalArgumentException Thrown if {@code name} does not fit the header.
   */
  private static void tarEntry(ByteArrayOutputStream tar, String name, byte[] content)
      throws IOException {
    final byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
    if (nameBytes.length == 0 || nameBytes.length > 100) {
      throw new IllegalArgumentException(
          "entry name must be 1..100 bytes, got " + nameBytes.length);
    }
    final byte[] header = new byte[512];
    System.arraycopy(nameBytes, 0, header, 0, nameBytes.length);
    final byte[] mode = "0000644".getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(mode, 0, header, 100, mode.length);
    final String size = String.format("%011o", content.length);
    System.arraycopy(size.getBytes(StandardCharsets.US_ASCII), 0, header, 124, 11);
    header[156] = '0';
    for (int i = 148; i < 156; i++) {
      header[i] = ' ';
    }
    int checksum = 0;
    for (final byte b : header) {
      checksum += b & 0xFF;
    }
    final String checksumText = String.format("%06o", checksum);
    System.arraycopy(checksumText.getBytes(StandardCharsets.US_ASCII), 0, header, 148, 6);
    header[154] = 0;
    header[155] = ' ';
    tar.write(header);
    tar.write(content);
    final int padding = (512 - content.length % 512) % 512;
    tar.write(new byte[padding]);
  }

  /**
   * Builds a gzip-compressed tar archive from name and content pairs, the layout a
   * dictionary distribution ships in.
   *
   * @param entries The entries as {@code {name, content}} pairs. Must not be
   *                {@code null}.
   * @return The compressed archive bytes. Never {@code null}.
   * @throws IOException Thrown if writing to the in-memory streams fails.
   */
  private static byte[] gzippedTar(String[][] entries) throws IOException {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    for (final String[] entry : entries) {
      tarEntry(tar, entry[0], entry[1].getBytes(StandardCharsets.UTF_8));
    }
    tar.write(new byte[1024]);
    final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
      gzip.write(tar.toByteArray());
    }
    return compressed.toByteArray();
  }

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
    final byte[] archive = gzippedTar(new String[][] {
        {"mini-dict-0.1/lexicon.csv", String.join("\n",
            "東京,0,0,3000,noun,proper",
            "京都,0,0,3000,noun,proper",
            "東,0,0,6000,noun,common",
            "都,0,0,4000,noun,suffix",
            "に,0,0,1000,particle,case",
            "行く,0,0,3000,verb,base",
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
    final String text = "東京都に行く";
    Assertions.assertArrayEquals(
        new String[] {"東京", "都", "に", "行く"},
        tokenizer.tokenize(text));
    Assertions.assertArrayEquals(new Span[] {
        new Span(0, 2), new Span(2, 3), new Span(3, 4), new Span(4, 6)},
        tokenizer.tokenizePos(text));

    // The analyze view adds the dictionary's feature columns to every morpheme.
    final List<Morpheme> morphemes = tokenizer.analyze(text);
    Assertions.assertEquals(4, morphemes.size());
    Assertions.assertEquals("東京", morphemes.get(0).surface());
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
        "我 5000 r",
        "来到 2000 v",
        "北京 3000 ns",
        "天安门 1200 ns",
        "").getBytes(StandardCharsets.UTF_8));

    final UnigramSegmenter segmenter = UnigramSegmenter.load(lexicon);
    final String text = "我来到北京天安门";
    Assertions.assertArrayEquals(
        new String[] {"我", "来到", "北京", "天安门"},
        segmenter.tokenize(text));
    Assertions.assertArrayEquals(new Span[] {
        new Span(0, 1), new Span(1, 3), new Span(3, 5), new Span(5, 8)},
        segmenter.tokenizePos(text));
  }
}
