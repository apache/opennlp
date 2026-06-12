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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;

import opennlp.spellcheck.SuggestItem;
import opennlp.spellcheck.Verbosity;
import opennlp.spellcheck.symspell.SymSpellConfig;
import opennlp.tools.AbstractTempDirTest;
import opennlp.tools.models.simple.SimpleClassPathModelFinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SymSpellModelResolverTest extends AbstractTempDirTest {

  @Test
  void resolvesPackagedModelByLanguageFromClasspath() throws IOException {
    final SymSpellConfig config = SymSpellConfig.builder().maxDictionaryEditDistance(2).build();
    final SymSpellModel model = new SymSpellModel("en", config,
        Map.of("the", 100L, "world", 50L, "quick", 30L), Map.of());

    final Path jar = tempDir.resolve("opennlp-models-spellcheck-en-1.0.jar");
    final byte[] bytes = SymSpellModels.toBytes(model);
    try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar))) {
      jos.putNextEntry(new JarEntry("models/spellcheck-symspell-en.bin"));
      jos.write(bytes);
      jos.closeEntry();

      jos.putNextEntry(new JarEntry("model.properties"));
      SymSpellModels.buildProperties(model, bytes).store(jos, "test");
      jos.closeEntry();
    }

    final ClassLoader previous = Thread.currentThread().getContextClassLoader();
    try (URLClassLoader cl = new URLClassLoader(new URL[] {jar.toUri().toURL()}, previous)) {
      Thread.currentThread().setContextClassLoader(cl);

      final SymSpellModelResolver resolver = new SymSpellModelResolver(
          new SimpleClassPathModelFinder(), new opennlp.tools.models.ClassPathModelLoader(),
          SymSpellModelResolver.DEFAULT_NAME_FRAGMENT);

      final Optional<SymSpellModel> resolved = resolver.resolveByLanguage("en", true);
      assertTrue(resolved.isPresent(), "model should be resolvable from the classpath");

      final List<SuggestItem> r = resolved.get().getSymSpell().lookup("teh", Verbosity.TOP, 2);
      assertFalse(r.isEmpty());
      assertEquals("the", r.get(0).term());

      // A non-existent language must not resolve.
      assertTrue(resolver.resolveByLanguage("zz", true).isEmpty());
    } finally {
      Thread.currentThread().setContextClassLoader(previous);
    }
  }

  @Test
  void corruptSha256IsRejected() throws IOException {
    final SymSpellConfig config = SymSpellConfig.builder().maxDictionaryEditDistance(2).build();
    final SymSpellModel model = new SymSpellModel("en", config,
        Map.of("the", 100L, "world", 50L), Map.of());
    final byte[] bytes = SymSpellModels.toBytes(model);

    // Ship a descriptor whose declared SHA-256 does not match the bytes.
    final Properties props = SymSpellModels.buildProperties(model, bytes);
    props.setProperty(SymSpellModels.PROP_SHA256, "0".repeat(64));

    final Path jar = tempDir.resolve("opennlp-models-spellcheck-en-1.0.jar");
    try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar))) {
      jos.putNextEntry(new JarEntry("models/spellcheck-symspell-en.bin"));
      jos.write(bytes);
      jos.closeEntry();
      jos.putNextEntry(new JarEntry("model.properties"));
      props.store(jos, "test");
      jos.closeEntry();
    }

    final ClassLoader previous = Thread.currentThread().getContextClassLoader();
    try (URLClassLoader cl = new URLClassLoader(new URL[] {jar.toUri().toURL()}, previous)) {
      Thread.currentThread().setContextClassLoader(cl);
      final SymSpellModelResolver resolver = new SymSpellModelResolver(
          new SimpleClassPathModelFinder(), new opennlp.tools.models.ClassPathModelLoader(),
          SymSpellModelResolver.DEFAULT_NAME_FRAGMENT);
      final IOException ex = assertThrows(IOException.class,
          () -> resolver.resolveByLanguage("en", true));
      assertTrue(ex.getMessage().contains("integrity"), "message should flag the integrity failure");
    } finally {
      Thread.currentThread().setContextClassLoader(previous);
    }
  }

  @Test
  void deserializeThenReserializeIsStable() throws IOException {
    final SymSpellConfig config = SymSpellConfig.builder()
        .maxDictionaryEditDistance(2).prefixLength(7).build();
    final SymSpellModel model = new SymSpellModel("de", "spellcheck-symspell", "2.1", config,
        Map.of("haus", 100L, "maus", 80L), Map.of("das haus", 10L));

    final byte[] bytes = SymSpellModels.toBytes(model);
    final SymSpellModel restored;
    try (InputStream in = new ByteArrayInputStream(bytes)) {
      restored = SymSpellModels.deserialize(in);
    }
    assertEquals("de", restored.getLanguage());
    assertEquals("spellcheck-symspell", restored.getName());
    assertEquals("2.1", restored.getVersion());
    assertEquals(model.unigrams(), restored.unigrams());
    assertEquals(model.bigrams(), restored.bigrams());
  }
}
