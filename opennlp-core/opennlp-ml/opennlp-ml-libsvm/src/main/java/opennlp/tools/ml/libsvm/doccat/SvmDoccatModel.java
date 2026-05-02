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

package opennlp.tools.ml.libsvm.doccat;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import de.hhn.mi.domain.SvmModel;

/**
 * A model for SVM-based document categorization. This model wraps a zlibsvm
 * {@link SvmModel} together with the feature vocabulary, category label
 * mappings, corpus statistics, and configuration required for classification.
 * <p>
 * Persistence uses Java object serialization via {@link #serialize(OutputStream)}
 * and {@link #deserialize(InputStream)}. Reads are guarded by an
 * {@link java.io.ObjectInputFilter ObjectInputFilter} that allow-lists only the
 * classes reachable from a legitimate {@code SvmDoccatModel} graph and bounds
 * graph depth, references, and array length. Foreign payloads are rejected
 * with {@link java.io.InvalidClassException} before being materialised.
 * <p>
 * Treat the filter as defense-in-depth, not as a license to deserialize from
 * untrusted sources: callers should still ensure the input stream originates
 * from a location they trust.
 *
 * @see DocumentCategorizerSVM
 */
public class SvmDoccatModel implements Serializable {

  @Serial
  private static final long serialVersionUID = 2L;

  private final SvmModel svmModel;
  private final HashMap<String, Integer> featureVocabulary;
  private final HashMap<Integer, String> indexToCategory;
  private final HashMap<String, Integer> categoryToIndex;
  private final HashMap<String, Double> idfValues;
  private final HashMap<Integer, Double> featureMinValues;
  private final HashMap<Integer, Double> featureMaxValues;
  private final SvmDoccatConfiguration configuration;
  private final String languageCode;

  /**
   * Instantiates a {@link SvmDoccatModel} with the given parameters.
   *
   * @param svmModel          The trained {@link SvmModel}. Must not be {@code null}.
   * @param featureVocabulary  A mapping from feature strings to their numeric indices.
   *                           Must not be {@code null}.
   * @param indexToCategory    A mapping from numeric category labels to category names.
   *                           Must not be {@code null}.
   * @param categoryToIndex   A mapping from category names to numeric labels.
   *                           Must not be {@code null}.
   * @param idfValues          A mapping from feature strings to their IDF values.
   *                           Must not be {@code null}.
   * @param featureMinValues   A mapping from feature index to its minimum value in the
   *                           training corpus (for scaling). Must not be {@code null}.
   * @param featureMaxValues   A mapping from feature index to its maximum value in the
   *                           training corpus (for scaling). Must not be {@code null}.
   * @param configuration      The {@link SvmDoccatConfiguration} used for training.
   *                           Must not be {@code null}.
   * @param languageCode      An ISO conform language code.
   * @throws NullPointerException if any argument other than {@code languageCode}
   *                              is {@code null}.
   */
  SvmDoccatModel(SvmModel svmModel,
                  Map<String, Integer> featureVocabulary,
                  Map<Integer, String> indexToCategory,
                  Map<String, Integer> categoryToIndex,
                  Map<String, Double> idfValues,
                  Map<Integer, Double> featureMinValues,
                  Map<Integer, Double> featureMaxValues,
                  SvmDoccatConfiguration configuration,
                  String languageCode) {
    this.svmModel = Objects.requireNonNull(svmModel, "svmModel must not be null");
    this.featureVocabulary = new HashMap<>(
        Objects.requireNonNull(featureVocabulary, "featureVocabulary must not be null"));
    this.indexToCategory = new HashMap<>(
        Objects.requireNonNull(indexToCategory, "indexToCategory must not be null"));
    this.categoryToIndex = new HashMap<>(
        Objects.requireNonNull(categoryToIndex, "categoryToIndex must not be null"));
    this.idfValues = new HashMap<>(
        Objects.requireNonNull(idfValues, "idfValues must not be null"));
    this.featureMinValues = new HashMap<>(
        Objects.requireNonNull(featureMinValues, "featureMinValues must not be null"));
    this.featureMaxValues = new HashMap<>(
        Objects.requireNonNull(featureMaxValues, "featureMaxValues must not be null"));
    this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    this.languageCode = languageCode;
  }

  /**
   * @return The underlying {@link SvmModel}.
   */
  public SvmModel getSvmModel() {
    return svmModel;
  }

  /**
   * @return An unmodifiable mapping from feature strings to their numeric indices.
   */
  public Map<String, Integer> getFeatureVocabulary() {
    return Collections.unmodifiableMap(featureVocabulary);
  }

  /**
   * @return An unmodifiable mapping from numeric category labels to category names.
   */
  public Map<Integer, String> getIndexToCategory() {
    return Collections.unmodifiableMap(indexToCategory);
  }

  /**
   * @return An unmodifiable mapping from category names to numeric labels.
   */
  public Map<String, Integer> getCategoryToIndex() {
    return Collections.unmodifiableMap(categoryToIndex);
  }

  /**
   * @return An unmodifiable mapping from feature strings to their IDF values.
   */
  public Map<String, Double> getIdfValues() {
    return Collections.unmodifiableMap(idfValues);
  }

  /**
   * @return An unmodifiable mapping from feature index to its minimum value in the
   *         training corpus.
   */
  public Map<Integer, Double> getFeatureMinValues() {
    return Collections.unmodifiableMap(featureMinValues);
  }

  /**
   * @return An unmodifiable mapping from feature index to its maximum value in the
   *         training corpus.
   */
  public Map<Integer, Double> getFeatureMaxValues() {
    return Collections.unmodifiableMap(featureMaxValues);
  }

  /**
   * @return The {@link SvmDoccatConfiguration} used for this model.
   */
  public SvmDoccatConfiguration getConfiguration() {
    return configuration;
  }

  /**
   * @return The ISO language code associated with this model.
   */
  public String getLanguageCode() {
    return languageCode;
  }

  /**
   * @return The number of categories in this model.
   */
  public int getNumberOfCategories() {
    return indexToCategory.size();
  }

  /**
   * Serializes this model to the given {@link OutputStream} using Java object
   * serialization. The resulting stream can be read back with
   * {@link #deserialize(InputStream)}.
   *
   * @param out The {@link OutputStream} to write to. Must not be {@code null}.
   * @throws IOException Thrown if IO errors occurred during serialization.
   * @throws IllegalArgumentException if {@code out} is {@code null}.
   */
  public void serialize(OutputStream out) throws IOException {
    if (out == null) {
      throw new IllegalArgumentException("out must not be null");
    }
    try (ObjectOutputStream oos = new ObjectOutputStream(out)) {
      oos.writeObject(this);
    }
  }

  /**
   * Deserializes a {@link SvmDoccatModel} from the given {@link InputStream}
   * using {@link DeserializationLimits#DEFAULT default} resource limits.
   * <p>
   * The stream is filtered via an {@link ObjectInputFilter} that allow-lists
   * only the classes required to reconstruct an {@link SvmDoccatModel}, plus
   * resource limits on graph depth, references, and array length. Foreign
   * payloads are rejected with {@link java.io.InvalidClassException} before
   * {@link ObjectInputStream#readObject()} returns.
   * <p>
   * Callers should still treat this method as defense-in-depth: only invoke
   * it on streams from trusted sources.
   * <p>
   * If the default limits are too tight for an unusually large model — for
   * example, a model with more than {@value #MAX_ARRAY_DEFAULT} entries in a
   * single map — use {@link #deserialize(InputStream, DeserializationLimits)}
   * to supply higher limits. The class allow-list is intentionally not
   * configurable; loosening it would defeat the purpose of the filter.
   *
   * @param in The {@link InputStream} to read from. Must not be {@code null}.
   * @return A valid {@link SvmDoccatModel} instance.
   * @throws IOException Thrown if IO errors occurred during deserialization,
   *                     including {@link java.io.InvalidClassException} when
   *                     the stream contains a class outside the allow-list or
   *                     exceeds a resource limit.
   * @throws ClassNotFoundException Thrown if required classes are not found.
   * @throws IllegalArgumentException if {@code in} is {@code null}.
   */
  public static SvmDoccatModel deserialize(InputStream in) throws IOException, ClassNotFoundException {
    return deserialize(in, DeserializationLimits.DEFAULT);
  }

  /**
   * Deserializes a {@link SvmDoccatModel} from the given {@link InputStream}
   * using the supplied {@link DeserializationLimits resource limits}.
   * <p>
   * Use this overload when the {@link DeserializationLimits#DEFAULT default
   * limits} reject a legitimate model — for example, models trained with very
   * large feature vocabularies. The class allow-list applied to the stream is
   * the same as for {@link #deserialize(InputStream)}; only the numeric
   * limits change.
   *
   * @param in     The {@link InputStream} to read from. Must not be {@code null}.
   * @param limits The {@link DeserializationLimits} to apply. Must not be
   *               {@code null}.
   * @return A valid {@link SvmDoccatModel} instance.
   * @throws IOException Thrown if IO errors occurred during deserialization,
   *                     including {@link java.io.InvalidClassException} when
   *                     the stream contains a class outside the allow-list or
   *                     exceeds one of the supplied limits.
   * @throws ClassNotFoundException Thrown if required classes are not found.
   * @throws IllegalArgumentException if {@code in} or {@code limits} is
   *                                  {@code null}.
   */
  public static SvmDoccatModel deserialize(InputStream in, DeserializationLimits limits)
      throws IOException, ClassNotFoundException {
    if (in == null) {
      throw new IllegalArgumentException("in must not be null");
    }
    if (limits == null) {
      throw new IllegalArgumentException("limits must not be null");
    }
    try (ObjectInputStream ois = new ObjectInputStream(in)) {
      ois.setObjectInputFilter(buildFilter(limits));
      return (SvmDoccatModel) ois.readObject();
    }
  }

  /**
   * Resource limits applied to the {@link ObjectInputFilter} used by
   * {@link SvmDoccatModel#deserialize(InputStream, DeserializationLimits)}.
   * <p>
   * The limits bound graph traversal regardless of the class allow-list and
   * provide defense-in-depth against pathological streams. The
   * {@linkplain #DEFAULT default values} are generous enough for typical
   * production models; raise them only if a legitimate model is rejected.
   *
   * @param maxDepth       Maximum object-graph nesting depth. Must be {@code > 0}.
   * @param maxRefs        Maximum number of internal references the stream may
   *                       create. Must be {@code > 0}.
   * @param maxArrayLength Maximum length of any single array allocation
   *                       requested by the stream. Must be {@code > 0}.
   * @throws IllegalArgumentException if any of {@code maxDepth},
   *                                  {@code maxRefs}, or {@code maxArrayLength}
   *                                  is {@code <= 0}.
   */
  public record DeserializationLimits(long maxDepth, long maxRefs, long maxArrayLength) {

    /**
     * Default limits. Sized to allow typical production-scale models to
     * round-trip while still bounding pathological streams.
     */
    public static final DeserializationLimits DEFAULT =
        new DeserializationLimits(MAX_DEPTH_DEFAULT, MAX_REFS_DEFAULT, MAX_ARRAY_DEFAULT);

    public DeserializationLimits {
      if (maxDepth <= 0) {
        throw new IllegalArgumentException("maxDepth must be > 0");
      }
      if (maxRefs <= 0) {
        throw new IllegalArgumentException("maxRefs must be > 0");
      }
      if (maxArrayLength <= 0) {
        throw new IllegalArgumentException("maxArrayLength must be > 0");
      }
    }
  }

  // Default resource limits applied during deserialization. These are
  // intentionally generous so that legitimate models — which may contain
  // millions of support vectors — round-trip without issue, while still
  // bounding pathological inputs.
  private static final long MAX_DEPTH_DEFAULT = 64;
  private static final long MAX_REFS_DEFAULT = 5_000_000;
  private static final long MAX_ARRAY_DEFAULT = 10_000_000;

  // Allow-list of fully qualified class names that may appear in the
  // serialized graph of a SvmDoccatModel. Anything else is rejected.
  private static final Set<String> ALLOWED_CLASSES = Set.of(
      // OpenNLP types persisted by this model
      "opennlp.tools.ml.libsvm.doccat.SvmDoccatModel",
      "opennlp.tools.ml.libsvm.doccat.SvmDoccatConfiguration",
      "opennlp.tools.ml.libsvm.doccat.TermWeightingStrategy",
      "opennlp.tools.ml.libsvm.doccat.FeatureSelectionStrategy",
      // zlibsvm domain + configuration types reachable from SvmModel
      "de.hhn.mi.configuration.SvmConfigurationImpl",
      "de.hhn.mi.configuration.SvmType",
      "de.hhn.mi.configuration.KernelType",
      "de.hhn.mi.domain.SvmModelImpl",
      "de.hhn.mi.domain.SvmMetaInformationImpl",
      "de.hhn.mi.domain.SvmFeatureImpl",
      "de.hhn.mi.domain.SvmClassLabelImpl",
      // Native libsvm structures embedded in SvmModelImpl
      "libsvm.svm_model",
      "libsvm.svm_node",
      "libsvm.svm_parameter",
      // JDK types used in field declarations. Note: ObjectInputStream
      // invokes the filter for every class descriptor in the inheritance
      // chain, not only for the runtime class — so the abstract superclasses
      // java.lang.Number (super of Integer/Double) and java.lang.Enum (super
      // of every concrete enum value in the graph) must be allow-listed even
      // though no instance of either appears in the stream.
      "java.lang.String",
      "java.lang.Number",
      "java.lang.Integer",
      "java.lang.Double",
      "java.lang.Boolean",
      "java.lang.Enum",
      "java.util.HashMap",
      // HashMap.readObject() requests permission to allocate a Map.Entry[]
      // before reading entries; the array type itself never appears as a
      // value in the stream.
      "java.util.Map$Entry"
  );

  private static ObjectInputFilter buildFilter(DeserializationLimits limits) {
    return info -> {
      if (info.depth() > limits.maxDepth()
          || info.references() > limits.maxRefs()
          || info.arrayLength() > limits.maxArrayLength()) {
        return ObjectInputFilter.Status.REJECTED;
      }

      Class<?> serialClass = info.serialClass();
      if (serialClass == null) {
        return ObjectInputFilter.Status.UNDECIDED;
      }

      Class<?> componentType = serialClass;
      while (componentType.isArray()) {
        componentType = componentType.getComponentType();
      }
      if (componentType.isPrimitive()) {
        return ObjectInputFilter.Status.ALLOWED;
      }

      return ALLOWED_CLASSES.contains(componentType.getName())
          ? ObjectInputFilter.Status.ALLOWED
          : ObjectInputFilter.Status.REJECTED;
    };
  }
}
