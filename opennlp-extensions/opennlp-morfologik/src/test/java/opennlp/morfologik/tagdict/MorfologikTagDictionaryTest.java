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

package opennlp.morfologik.tagdict;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import morfologik.stemming.Dictionary;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.morfologik.AbstractMorfologikTest;
import opennlp.morfologik.builder.MorfologikDictionaryBuilder;
import opennlp.tools.postag.TagDictionary;

/**
 * Tests for the {@link MorfologikTagDictionary} class.
 */
public class MorfologikTagDictionaryTest extends AbstractMorfologikTest {

  @Test
  public void testNoLemma() throws Exception {
    MorfologikTagDictionary dict = createDictionary(false);

    List<String> tags = Arrays.asList(dict.getTags("carro"));
    Assertions.assertEquals(1, tags.size());
    Assertions.assertTrue(tags.contains("NOUN"));
  }

  @Test
  public void testPOSDictionaryInsensitive() throws Exception {
    TagDictionary dict = createDictionary(false);

    List<String> tags = Arrays.asList(dict.getTags("casa"));
    Assertions.assertEquals(2, tags.size());
    Assertions.assertTrue(tags.contains("NOUN"));
    Assertions.assertTrue(tags.contains("V"));

    // this is the behavior of case insensitive dictionary
    // if we search it using case insensitive, Casa as a proper noun
    // should be lower case in the dictionary
    tags = Arrays.asList(dict.getTags("Casa"));
    Assertions.assertEquals(2, tags.size());
    Assertions.assertTrue(tags.contains("NOUN"));
    Assertions.assertTrue(tags.contains("V"));
  }

  @Test
  public void testPOSDictionarySensitive() throws Exception {
    TagDictionary dict = createDictionary(true);

    List<String> tags = Arrays.asList(dict.getTags("casa"));
    Assertions.assertEquals(2, tags.size());
    Assertions.assertTrue(tags.contains("NOUN"));
    Assertions.assertTrue(tags.contains("V"));

    // this is the behavior of case insensitive dictionary
    // if we search it using case insensitive, Casa as a proper noun
    // should be lower case in the dictionary
    tags = Arrays.asList(dict.getTags("Casa"));
    Assertions.assertEquals(1, tags.size());
    Assertions.assertTrue(tags.contains("PROP"));

  }

  /**
   * Verifies that a two-column Morfologik dictionary (lemma, inflected form, no tag)
   * reports {@code null} for {@link TagDictionary#getTags(String)} rather than throwing
   * when {@code WordData#getTag()} is null.
   */
  @Test
  public void testTaglessDictionaryReturnsNull() throws Exception {
    final Path output = createMorfologikDictionary("dictionaryTagless");
    output.toFile().deleteOnExit();
    final TagDictionary dict = new MorfologikTagDictionary(Dictionary.read(output), true);

    final String[] tags = Assertions.assertDoesNotThrow(() -> dict.getTags("carro"));
    Assertions.assertNull(tags);
  }

  /** Tests code-point lookup for sigma, dotted I and supplementary letters. */
  @Test
  void testCodePointCaseInsensitiveLookup(@TempDir Path directory) throws Exception {
    final Path input = directory.resolve("unicode.txt");
    Files.writeString(input, "οσ,οσ,SIGMA\ni,i,DOTTED_I\n𐐨,𐐨,DESERET\n",
        StandardCharsets.UTF_8);
    Files.writeString(directory.resolve("unicode.info"),
        "fsa.dict.separator=,\nfsa.dict.encoding=UTF-8\nfsa.dict.encoder=prefix\n",
        StandardCharsets.UTF_8);
    final Dictionary dictionary = Dictionary.read(new MorfologikDictionaryBuilder().build(input));
    final TagDictionary insensitive = new MorfologikTagDictionary(dictionary, false);
    final TagDictionary sensitive = new MorfologikTagDictionary(dictionary, true);
    final Map<String, String> cases = Map.of("ΟΣ", "SIGMA", "İ", "DOTTED_I", "𐐀", "DESERET");

    Assertions.assertAll(cases.entrySet().stream().map(entry -> () -> {
      Assertions.assertArrayEquals(new String[] {entry.getValue()},
          insensitive.getTags(entry.getKey()), entry.getKey());
      Assertions.assertNull(sensitive.getTags(entry.getKey()), entry.getKey());
    }));
  }

  private MorfologikTagDictionary createDictionary(boolean caseSensitive)
      throws Exception {
    return this.createDictionary(caseSensitive, null);
  }

  private MorfologikTagDictionary createDictionary(boolean caseSensitive,
      List<String> constant) throws Exception {
    Path output = createMorfologikDictionary();
    output.toFile().deleteOnExit();
    Dictionary dic = Dictionary.read(output);
    return new MorfologikTagDictionary(dic, caseSensitive);
  }

}
