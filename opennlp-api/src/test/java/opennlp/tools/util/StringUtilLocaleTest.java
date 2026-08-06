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

package opennlp.tools.util;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests that the {@link StringUtil} operations feeding the lemmatizer's shortest edit script
 * are independent of the JVM's default {@link Locale}.
 */
public class StringUtilLocaleTest {

  /**
   * Turkish folds {@code 'I'} to the dotless {@code 'ı'} (U+0131) instead of {@code 'i'},
   * which makes it the canonical probe for an unqualified {@code String#toLowerCase()}.
   */
  private static final Locale TURKISH = Locale.of("tr", "TR");

  private final Locale defaultLocale = Locale.getDefault();

  @AfterEach
  void restoreDefaultLocale() {
    Locale.setDefault(defaultLocale);
  }

  /**
   * The shortest edit script becomes an outcome label inside a trained lemmatizer model.
   * If it varied with the default locale, a model trained on a Turkish JVM would carry
   * labels no other JVM could reproduce.
   */
  @Test
  void testGetShortestEditScriptIsIndependentOfDefaultLocale() {
    Locale.setDefault(TURKISH);

    Assertions.assertEquals("D0s", StringUtil.getShortestEditScript("IMPORTS", "import"));
    Assertions.assertEquals("R2ioR1cuI1s", StringUtil.getShortestEditScript("MICE", "mouse"));
    Assertions.assertEquals("D3iD2cR0sx", StringUtil.getShortestEditScript("INDICES", "index"));
    Assertions.assertEquals("O", StringUtil.getShortestEditScript("Illinois", "illinois"));
  }

  /**
   * Guards the choice of {@link Locale#ROOT} over the code point based
   * {@link StringUtil#toLowerCase(CharSequence)}: the two disagree on context sensitive
   * mappings such as the Greek final sigma. Existing models were trained with
   * {@link Locale#ROOT} semantics, so only {@link Locale#ROOT} keeps them readable.
   */
  @Test
  void testGetShortestEditScriptUsesRootFoldingNotCodePointFolding() {
    // Locale.ROOT maps the trailing capital sigma to the final form 'ς', so both inputs
    // fold to the same string and no edit is required.
    Assertions.assertEquals("O", StringUtil.getShortestEditScript("ΟΔΟΣ",
        "οδος"));

    // Character#toLowerCase is context free and would yield the non final 'σ' instead,
    // which is a different string and hence a different, model breaking outcome label.
    Assertions.assertNotEquals("οδος",
        StringUtil.toLowerCase("ΟΔΟΣ"));
  }
}
