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

package opennlp.tools.lemmatizer;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests that the lemma class encoding and decoding of {@link LemmatizerME} do not depend
 * on the JVM's default {@link Locale}.
 * <p>
 * Encoding produces the outcome labels that are persisted in a trained model, decoding
 * consumes them at inference time. Both sides therefore have to agree across JVMs.
 */
public class LemmatizerMELocaleTest {

  /**
   * Turkish folds {@code 'I'} to the dotless {@code 'ı'} (U+0131) instead of {@code 'i'}.
   */
  private static final Locale TURKISH = Locale.of("tr", "TR");

  private static final String[] TOKENS = {"MICE", "INDICES"};
  private static final String[] LEMMAS = {"mouse", "index"};

  /** The lemma classes an existing, English trained model contains for {@link #TOKENS}. */
  private static final String[] LEMMA_CLASSES = {"R2ioR1cuI1s", "D3iD2cR0sx"};

  private final Locale defaultLocale = Locale.getDefault();

  @AfterEach
  void restoreDefaultLocale() {
    Locale.setDefault(defaultLocale);
  }

  /**
   * Training on a Turkish JVM must not write different outcome labels into the model.
   */
  @Test
  void testEncodeLemmasIsIndependentOfDefaultLocale() {
    Locale.setDefault(TURKISH);

    Assertions.assertArrayEquals(LEMMA_CLASSES, LemmatizerME.encodeLemmas(TOKENS, LEMMAS));
  }

  /**
   * Serving an existing model on a Turkish JVM must not corrupt the reconstructed lemmas.
   */
  @Test
  void testDecodeLemmasIsIndependentOfDefaultLocale() {
    Locale.setDefault(TURKISH);

    Assertions.assertArrayEquals(LEMMAS, LemmatizerME.decodeLemmas(TOKENS, LEMMA_CLASSES));
  }
}
