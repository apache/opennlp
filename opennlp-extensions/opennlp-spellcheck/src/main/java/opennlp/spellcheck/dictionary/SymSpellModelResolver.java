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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import opennlp.tools.models.ClassPathModel;
import opennlp.tools.models.ClassPathModelEntry;
import opennlp.tools.models.ClassPathModelFinder;
import opennlp.tools.models.ClassPathModelLoader;
import opennlp.tools.models.simple.SimpleClassPathModelFinder;

/**
 * Resolves packaged {@link SymSpellModel SymSpell models} from the classpath by language.
 *
 * <p>Production multi-language dictionaries are shipped as separate
 * {@code opennlp-models-spellcheck-{lang}} jars (not bundled in this module). Each such
 * jar contains a binary {@code *.bin} model written by {@link SymSpellModelSerializer}
 * and a matching {@code model.properties} carrying {@code model.language},
 * {@code model.name}, {@code model.version} and {@code model.sha256}.</p>
 *
 * <p>This resolver reuses the standard OpenNLP model-resolver scanning machinery
 * ({@link ClassPathModelFinder} + {@link ClassPathModelLoader}) to discover those jars,
 * selects the entry whose {@code model.language} matches the requested language (and
 * whose {@code model.name} identifies a spellcheck model), and deserializes its bytes
 * into a {@link SymSpellModel}.</p>
 *
 * <p>The {@code SymSpellModel} is not an OpenNLP {@code BaseModel}, so the typed
 * {@code ClassPathModelProvider.load(...)} path does not apply; this resolver instead
 * consumes the raw {@link ClassPathModel} ({@code properties + bytes}) directly.</p>
 */
public final class SymSpellModelResolver {

  /** Default {@code model.name} fragment used to recognize spellcheck models. */
  public static final String DEFAULT_NAME_FRAGMENT = "spellcheck";

  private final ClassPathModelFinder finder;
  private final ClassPathModelLoader loader;
  private final String nameFragment;

  /**
   * Creates a resolver that scans the default {@code opennlp-models-*.jar} prefix using a
   * {@link SimpleClassPathModelFinder} and recognizes spellcheck models by the default
   * name fragment.
   */
  public SymSpellModelResolver() {
    this(new SimpleClassPathModelFinder(), new ClassPathModelLoader(), DEFAULT_NAME_FRAGMENT);
  }

  /**
   * Creates a resolver with explicit collaborators.
   *
   * @param finder       the classpath model finder; must not be {@code null}
   * @param loader       the classpath model loader; must not be {@code null}
   * @param nameFragment a substring that the candidate's {@code model.name} must contain
   *                     to be considered a spellcheck model; must not be {@code null} or
   *                     blank
   */
  public SymSpellModelResolver(ClassPathModelFinder finder, ClassPathModelLoader loader,
                               String nameFragment) {
    this.finder = Objects.requireNonNull(finder, "finder must not be null");
    this.loader = Objects.requireNonNull(loader, "loader must not be null");
    if (nameFragment == null || nameFragment.isBlank()) {
      throw new IllegalArgumentException("nameFragment must not be null or blank");
    }
    this.nameFragment = nameFragment;
  }

  /**
   * Resolves a spellcheck model for the given language from the current classpath.
   *
   * @param language the language tag to match against {@code model.language}; must not be
   *                 {@code null} or blank
   * @return the resolved model, or {@link Optional#empty()} if no matching model jar is
   *     present on the classpath
   * @throws IOException Thrown on IO errors while reading classpath resources or on a
   *                     malformed model stream.
   */
  public Optional<SymSpellModel> resolveByLanguage(String language) throws IOException {
    return resolveByLanguage(language, false);
  }

  /**
   * Resolves a spellcheck model for the given language from the current classpath.
   *
   * @param language    the language tag to match against {@code model.language}; must not
   *                    be {@code null} or blank
   * @param reloadCache {@code true} to force the finder to rescan the classpath
   * @return the resolved model, or {@link Optional#empty()} if no matching model jar is
   *     present on the classpath
   * @throws IOException Thrown on IO errors while reading classpath resources or on a
   *                     malformed model stream.
   */
  public Optional<SymSpellModel> resolveByLanguage(String language, boolean reloadCache)
      throws IOException {
    if (language == null || language.isBlank()) {
      throw new IllegalArgumentException("language must not be null or blank");
    }

    final Set<ClassPathModelEntry> entries = finder.findModels(reloadCache);
    for (ClassPathModelEntry entry : entries) {
      // A spellcheck model needs both binary bytes and a properties descriptor; skip any
      // entry that lacks either so the loader (which requires a non-null model URI) is not
      // handed an incomplete entry.
      if (entry.model() == null || entry.properties().isEmpty()) {
        continue;
      }
      final ClassPathModel cpm = loader.load(entry);
      if (cpm == null || cpm.model() == null) {
        continue;
      }
      if (language.equals(cpm.getModelLanguage())
          && cpm.getModelName().contains(nameFragment)) {
        verifyIntegrity(cpm);
        try (InputStream in = new ByteArrayInputStream(cpm.model())) {
          return Optional.of(SymSpellModels.deserialize(in));
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Verifies the binary model against the {@code model.sha256} descriptor property when one
   * is present, so a corrupted or truncated artifact is rejected up front rather than failing
   * obscurely during deserialization.
   *
   * @param cpm the loaded classpath model to check
   * @throws IOException if the descriptor declares a SHA-256 that the bytes do not match
   */
  private static void verifyIntegrity(ClassPathModel cpm) throws IOException {
    final String expected = cpm.getModelSHA256();
    if (expected == null || expected.isBlank() || "unknown".equals(expected)) {
      return; // no integrity field shipped with this model
    }
    final String actual = SymSpellModels.sha256Hex(cpm.model());
    if (!expected.equalsIgnoreCase(actual)) {
      throw new IOException("spellcheck model integrity check failed: " + SymSpellModels.PROP_SHA256
          + " declared " + expected + " but the model bytes hash to " + actual);
    }
  }
}
