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

package opennlp.morfologik;

import java.nio.file.Path;
import java.util.Locale;

import morfologik.stemming.Dictionary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.morfologik.lemmatizer.MorfologikLemmatizer;
import opennlp.morfologik.tagdict.MorfologikTagDictionary;

/**
 * Tests that the case insensitive lookups into a prebuilt Morfologik FSA dictionary do not
 * depend on the JVM's default {@link Locale}.
 * <p>
 * The FSA is built once and shipped; folding the query with the default locale makes the
 * same dictionary answer differently on different JVMs.
 */
public class MorfologikLocaleTest extends AbstractMorfologikTest {

  /**
   * Turkish folds {@code 'I'} to the dotless {@code 'ı'} (U+0131) instead of {@code 'i'}.
   */
  private static final Locale TURKISH = Locale.of("tr", "TR");

  private final Locale defaultLocale = Locale.getDefault();

  @AfterEach
  void restoreDefaultLocale() {
    Locale.setDefault(defaultLocale);
  }

  @Test
  public void testLemmatizeIsIndependentOfDefaultLocale() throws Exception {
    final MorfologikLemmatizer lemmatizer = new MorfologikLemmatizer(createLocaleDictionary());

    Locale.setDefault(TURKISH);

    final String[] lemmas = lemmatizer.lemmatize(
        new String[] {"Illinois", "INDICES"}, new String[] {"PROP", "NOUN"});

    Assertions.assertArrayEquals(new String[] {"Illinois", "index"}, lemmas);
  }

  @Test
  public void testGetTagsIsIndependentOfDefaultLocale() throws Exception {
    final MorfologikTagDictionary tagDictionary =
        new MorfologikTagDictionary(Dictionary.read(createLocaleDictionary()), false);

    Locale.setDefault(TURKISH);

    Assertions.assertArrayEquals(new String[] {"PROP"}, tagDictionary.getTags("Illinois"));
    Assertions.assertArrayEquals(new String[] {"NOUN"}, tagDictionary.getTags("INDICES"));
  }

  private static Path createLocaleDictionary() throws Exception {
    final Path output = createMorfologikDictionary("dictionaryLocaleSafety");
    output.toFile().deleteOnExit();
    return output;
  }
}
