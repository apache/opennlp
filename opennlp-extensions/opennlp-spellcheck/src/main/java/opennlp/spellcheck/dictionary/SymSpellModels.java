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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import opennlp.spellcheck.symspell.SymSpellConfig;
import opennlp.tools.util.InputStreamFactory;

/**
 * Convenience factory and (de)serialization helpers for {@link SymSpellModel}.
 *
 * <p>This is the high-level entry point for the persistence layer: build a model from
 * plain-text frequency dictionaries, write a model to / read it from a stream, and emit
 * the {@code model.properties} descriptor consumed by the OpenNLP model-resolver.</p>
 *
 * <p>Classpath resolution of packaged models lives in {@link SymSpellModelResolver}.</p>
 */
public final class SymSpellModels {

  /** Property key for the model language tag. */
  public static final String PROP_LANGUAGE = "model.language";

  /** Property key for the model name. */
  public static final String PROP_NAME = "model.name";

  /** Property key for the model version. */
  public static final String PROP_VERSION = "model.version";

  /** Property key for the SHA-256 of the binary model. */
  public static final String PROP_SHA256 = "model.sha256";

  /** The Maven artifactId pattern for packaged spellcheck model jars. */
  public static final String MODEL_ARTIFACT_PREFIX = "opennlp-models-spellcheck-";

  private SymSpellModels() {
  }

  /**
   * Builds a {@link SymSpellModel} from a unigram dictionary and an optional bigram
   * dictionary using the supplied configuration.
   *
   * <p>The dictionaries are parsed with a {@link FrequencyDictionaryLoader} into source
   * count maps (with duplicate keys accumulated, mirroring engine semantics); the engine
   * itself is then built by {@link SymSpellModel}.</p>
   *
   * @param language        the language tag (e.g. {@code "en"}); must not be blank
   * @param config          the engine configuration; must not be {@code null}
   * @param charset         the charset to decode the dictionaries with; must not be
   *                        {@code null}
   * @param unigramSource   the {@code word<TAB>count} dictionary source; must not be
   *                        {@code null}
   * @param bigramSource    the {@code w1 w2<TAB>count} dictionary source; may be
   *                        {@code null} to skip bigrams
   * @return the built model
   * @throws IOException Thrown on IO errors or a malformed dictionary line.
   */
  public static SymSpellModel buildModel(String language, SymSpellConfig config, Charset charset,
                                         InputStreamFactory unigramSource,
                                         InputStreamFactory bigramSource) throws IOException {
    Objects.requireNonNull(config, "config must not be null");
    Objects.requireNonNull(charset, "charset must not be null");
    Objects.requireNonNull(unigramSource, "unigramSource must not be null");

    final FrequencyDictionaryLoader loader = new FrequencyDictionaryLoader(charset);

    final Map<String, Long> unigrams = new LinkedHashMap<>();
    loader.parseUnigrams(unigramSource, unigrams);

    final Map<String, Long> bigrams = new LinkedHashMap<>();
    if (bigramSource != null) {
      loader.parseBigrams(bigramSource, bigrams);
    }

    return new SymSpellModel(language, config, unigrams, bigrams);
  }

  /**
   * Serializes a model to the given stream using {@link SymSpellModelSerializer}.
   *
   * @param model the model to write; must not be {@code null}
   * @param out   the destination stream; must not be {@code null}. The stream is
   *              <b>not</b> closed by this method.
   * @throws IOException Thrown on IO errors.
   */
  public static void serialize(SymSpellModel model, OutputStream out) throws IOException {
    new SymSpellModelSerializer().serialize(model, out);
  }

  /**
   * Deserializes a model from the given stream using {@link SymSpellModelSerializer}.
   *
   * @param in the source stream; must not be {@code null}. The stream is <b>not</b>
   *           closed by this method.
   * @return the deserialized model
   * @throws IOException Thrown on IO errors or on a malformed stream.
   */
  public static SymSpellModel deserialize(InputStream in) throws IOException {
    return new SymSpellModelSerializer().create(in);
  }

  /**
   * Serializes a model to a byte array.
   *
   * @param model the model to serialize; must not be {@code null}
   * @return the binary model bytes
   * @throws IOException Thrown on IO errors.
   */
  public static byte[] toBytes(SymSpellModel model) throws IOException {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    serialize(model, baos);
    return baos.toByteArray();
  }

  /**
   * Deserializes a model from a byte array.
   *
   * @param bytes the binary model bytes; must not be {@code null}
   * @return the deserialized model
   * @throws IOException Thrown on IO errors or on a malformed stream.
   */
  public static SymSpellModel fromBytes(byte[] bytes) throws IOException {
    try (InputStream in = new ByteArrayInputStream(bytes)) {
      return deserialize(in);
    }
  }

  /**
   * Builds the {@code model.properties} descriptor for a serialized model, computing the
   * {@code model.sha256} over the supplied binary form.
   *
   * @param model       the model the properties describe; must not be {@code null}
   * @param modelBytes  the serialized binary form of {@code model} (see {@link #toBytes});
   *                    must not be {@code null}
   * @return the populated {@link Properties}
   */
  public static Properties buildProperties(SymSpellModel model, byte[] modelBytes) {
    Objects.requireNonNull(model, "model must not be null");
    Objects.requireNonNull(modelBytes, "modelBytes must not be null");
    final Properties props = new Properties();
    props.setProperty(PROP_LANGUAGE, model.getLanguage());
    props.setProperty(PROP_NAME, model.getName());
    props.setProperty(PROP_VERSION, model.getVersion());
    props.setProperty(PROP_SHA256, sha256Hex(modelBytes));
    return props;
  }

  /**
   * Writes a packaged model pair to the given streams: the binary model and the matching
   * {@code model.properties}. This is the on-disk shape expected inside an
   * {@code opennlp-models-spellcheck-{lang}} jar (the binary entry must have a
   * {@code .bin} suffix to be discoverable by the model-resolver).
   *
   * @param model         the model to package; must not be {@code null}
   * @param binaryOut     destination for the binary model; must not be {@code null}.
   *                      Not closed by this method.
   * @param propertiesOut destination for {@code model.properties}; must not be
   *                      {@code null}. Not closed by this method.
   * @throws IOException Thrown on IO errors.
   */
  public static void writePackage(SymSpellModel model, OutputStream binaryOut,
                                  OutputStream propertiesOut) throws IOException {
    final byte[] bytes = toBytes(model);
    binaryOut.write(bytes);
    binaryOut.flush();
    final Properties props = buildProperties(model, bytes);
    props.store(propertiesOut, "OpenNLP SymSpell spellcheck model descriptor");
    propertiesOut.flush();
  }

  /**
   * @param language a language tag; must not be {@code null}
   * @return the conventional Maven artifactId for a packaged model of that language,
   *     e.g. {@code "opennlp-models-spellcheck-en"}
   */
  public static String artifactId(String language) {
    return MODEL_ARTIFACT_PREFIX + Objects.requireNonNull(language, "language must not be null");
  }

  static String sha256Hex(byte[] data) {
    try {
      final MessageDigest md = MessageDigest.getInstance("SHA-256");
      final byte[] digest = md.digest(data);
      final StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(Character.forDigit((b >> 4) & 0xF, 16));
        sb.append(Character.forDigit(b & 0xF, 16));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is guaranteed to be available on every conformant JRE.
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
