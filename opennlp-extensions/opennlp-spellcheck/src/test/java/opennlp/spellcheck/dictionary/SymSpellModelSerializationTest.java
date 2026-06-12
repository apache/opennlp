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

package opennlp.spellcheck.dictionary;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.spellcheck.SuggestItem;
import opennlp.spellcheck.Verbosity;
import opennlp.spellcheck.symspell.SymSpell;
import opennlp.spellcheck.symspell.SymSpellConfig;
import opennlp.tools.util.InputStreamFactory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SymSpellModelSerializationTest {

  private static final String UNIGRAMS = "/opennlp/spellcheck/frequency_dictionary_tiny.txt";
  private static final String BIGRAMS = "/opennlp/spellcheck/frequency_bigramdictionary_tiny.txt";

  /** A few representative misspellings drawn from misspellings_tiny.txt. */
  private static final String[][] CASES = {
      {"teh", "the"},
      {"recieve", "receive"},
      {"wrold", "world"},
      {"quikc", "quick"},
      {"becuse", "because"},
      {"calender", "calendar"},
  };

  private SymSpellModel model;

  @BeforeEach
  void buildModel() throws IOException {
    final SymSpellConfig config = SymSpellConfig.builder().maxDictionaryEditDistance(2).build();
    model = SymSpellModels.buildModel("en", config,
        FrequencyDictionaryLoader.DEFAULT_CHARSET,
        resource(UNIGRAMS), resource(BIGRAMS));
    assertTrue(model.unigrams().size() > 0);
    assertTrue(model.bigrams().size() > 0);
  }

  @Test
  void loadersFeedAnEngineDirectly() throws IOException {
    final SymSpell engine = new SymSpell(SymSpellConfig.builder().maxDictionaryEditDistance(2).build());
    final FrequencyDictionaryLoader loader = new FrequencyDictionaryLoader();
    final long unigrams = loader.loadUnigrams(engine, resource(UNIGRAMS));
    final long bigrams = loader.loadBigrams(engine, resource(BIGRAMS));
    assertEquals(unigrams, engine.wordCount());
    assertEquals(bigrams, engine.bigramCount());
    final List<SuggestItem> r = engine.lookup("teh", Verbosity.TOP, 2);
    assertFalse(r.isEmpty());
    assertEquals("the", r.get(0).term());
  }

  @Test
  void serializationRoundTripIsLossless() throws IOException {
    final byte[] bytes = SymSpellModels.toBytes(model);
    final SymSpellModel restored;
    try (InputStream in = new ByteArrayInputStream(bytes)) {
      restored = SymSpellModels.deserialize(in);
    }

    // Metadata and source data must be identical.
    assertEquals(model.getLanguage(), restored.getLanguage());
    assertEquals(model.getName(), restored.getName());
    assertEquals(model.getVersion(), restored.getVersion());
    assertEquals(model.getConfig().maxDictionaryEditDistance(),
        restored.getConfig().maxDictionaryEditDistance());
    assertEquals(model.getConfig().prefixLength(), restored.getConfig().prefixLength());
    assertEquals(model.getConfig().countThreshold(), restored.getConfig().countThreshold());
    assertEquals(model.getConfig().editDistance().getClass(),
        restored.getConfig().editDistance().getClass());
    assertEquals(model.unigrams(), restored.unigrams());
    assertEquals(model.bigrams(), restored.bigrams());

    // The engines must yield identical suggestions for the sample misspellings.
    final SymSpell before = model.getSymSpell();
    final SymSpell after = restored.getSymSpell();
    for (String[] c : CASES) {
      final List<SuggestItem> a = before.lookup(c[0], Verbosity.ALL, 2);
      final List<SuggestItem> b = after.lookup(c[0], Verbosity.ALL, 2);
      assertEquals(a, b, "suggestions differ for '" + c[0] + "'");
      assertFalse(b.isEmpty(), "no suggestion for '" + c[0] + "'");
      assertEquals(c[1], b.get(0).term(), "wrong top suggestion for '" + c[0] + "'");
    }

    // Re-serializing the restored model must yield identical bytes (stable format).
    final byte[] again = SymSpellModels.toBytes(restored);
    assertArrayEquals(bytes, again);
  }

  @Test
  void pinnedCorpusWordCountSurvivesRoundTrip() throws IOException {
    final SymSpellConfig config = SymSpellConfig.builder()
        .maxDictionaryEditDistance(2).corpusWordCount(987_654_321L).build();
    final SymSpellModel pinned = SymSpellModels.buildModel("en", config,
        FrequencyDictionaryLoader.DEFAULT_CHARSET, resource(UNIGRAMS), resource(BIGRAMS));

    final SymSpellModel restored = SymSpellModels.fromBytes(SymSpellModels.toBytes(pinned));
    assertEquals(987_654_321L, restored.getConfig().corpusWordCount());
  }

  @Test
  void derivedCorpusWordCountRoundTripsAsSentinel() throws IOException {
    // The default config derives N; the sentinel (0) must round-trip unchanged.
    final SymSpellModel restored = SymSpellModels.fromBytes(SymSpellModels.toBytes(model));
    assertEquals(SymSpellConfig.DERIVE_CORPUS_WORD_COUNT, restored.getConfig().corpusWordCount());
  }

  @Test
  void compoundCorrectionSurvivesRoundTrip() throws IOException {
    final SymSpellModel restored = SymSpellModels.fromBytes(SymSpellModels.toBytes(model));
    final List<SuggestItem> r = restored.getSymSpell().lookupCompound("helloworld", 2);
    assertEquals(1, r.size());
    assertEquals("hello world", r.get(0).term());
  }

  @Test
  void modelPropertiesCarryResolverKeys() throws IOException {
    final byte[] bytes = SymSpellModels.toBytes(model);
    final Properties props = SymSpellModels.buildProperties(model, bytes);
    assertEquals("en", props.getProperty(SymSpellModels.PROP_LANGUAGE));
    assertEquals(SymSpellModel.DEFAULT_MODEL_NAME, props.getProperty(SymSpellModels.PROP_NAME));
    assertNotNull(props.getProperty(SymSpellModels.PROP_VERSION));
    final String sha = props.getProperty(SymSpellModels.PROP_SHA256);
    assertNotNull(sha);
    assertEquals(64, sha.length());
    assertEquals(SymSpellModels.sha256Hex(bytes), sha);
  }

  @Test
  void serializerReportsArtifactSerializerClass() {
    assertEquals(SymSpellModelSerializer.class, model.getArtifactSerializerClass());
  }

  @Test
  void loaderSkipsBlankAndCommentLines() throws IOException {
    final String text = "the\t100\n\n# a comment\n   \nworld\t50\n";
    final Map<String, Long> into = new java.util.LinkedHashMap<>();
    final long read = new FrequencyDictionaryLoader().parseUnigrams(stringResource(text), into);
    assertEquals(2, read);
    assertEquals(100L, into.get("the"));
    assertEquals(50L, into.get("world"));
  }

  @Test
  void loaderRejectsMalformedLine() {
    final String text = "the\tnotanumber\n";
    final Map<String, Long> into = new java.util.LinkedHashMap<>();
    final FrequencyDictionaryLoader loader = new FrequencyDictionaryLoader();
    final MalformedDictionaryLineException ex = org.junit.jupiter.api.Assertions.assertThrows(
        MalformedDictionaryLineException.class,
        () -> loader.parseUnigrams(stringResource(text), into));
    assertEquals(1, ex.getLineNumber());
  }

  private static InputStreamFactory resource(String path) {
    return () -> {
      final InputStream in = SymSpellModelSerializationTest.class.getResourceAsStream(path);
      if (in == null) {
        throw new IOException("test resource not found: " + path);
      }
      return in;
    };
  }

  private static InputStreamFactory stringResource(String text) {
    return () -> new ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
