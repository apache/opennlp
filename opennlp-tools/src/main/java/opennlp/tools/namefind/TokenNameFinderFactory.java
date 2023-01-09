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

package opennlp.tools.namefind;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import opennlp.tools.namefind.TokenNameFinderModel.FeatureGeneratorCreationError;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.SequenceCodec;
import opennlp.tools.util.ext.ExtensionLoader;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;
import opennlp.tools.util.featuregen.AggregatedFeatureGenerator;
import opennlp.tools.util.featuregen.BigramNameFeatureGenerator;
import opennlp.tools.util.featuregen.CachedFeatureGenerator;
import opennlp.tools.util.featuregen.GeneratorFactory;
import opennlp.tools.util.featuregen.OutcomePriorFeatureGenerator;
import opennlp.tools.util.featuregen.PreviousMapFeatureGenerator;
import opennlp.tools.util.featuregen.SentenceFeatureGenerator;
import opennlp.tools.util.featuregen.TokenClassFeatureGenerator;
import opennlp.tools.util.featuregen.TokenFeatureGenerator;
import opennlp.tools.util.featuregen.WindowFeatureGenerator;

/**
 * The factory that provides {@link TokenNameFinder} default implementations and
 * resources. That only works if that's the central class used for training/runtime.
 */
public class TokenNameFinderFactory extends BaseToolFactory {

  private byte[] featureGeneratorBytes;
  private Map<String, Object> resources;
  private SequenceCodec<String> seqCodec;

  /**
   * Initializes a {@link TokenNameFinderFactory} that provides the default implementation
   * of the resources. {@link BioCodec} will be used as default {@link SequenceCodec}.
   */
  public TokenNameFinderFactory() {
    this.seqCodec = new BioCodec();
  }

  /**
   * Initializes a {@link TokenNameFinderFactory} instance via given parameters.
   *
   * @param featureGeneratorBytes The {@code byte[]} representing the feature generator descriptor.
   * @param resources Additional resources in a mapping.
   * @param seqCodec The {@link SequenceCodec} to use.
   */
  public TokenNameFinderFactory(byte[] featureGeneratorBytes, final Map<String, Object> resources,
                                SequenceCodec<String> seqCodec) {
    init(featureGeneratorBytes, resources, seqCodec);
  }

  /**
   * Initializes via given parameters.
   *
   * @param featureGeneratorBytes The {@code byte[]} representing the feature generator descriptor.
   * @param resources Additional resources in a mapping.
   * @param seqCodec The {@link SequenceCodec} to use.
   */
  void init(byte[] featureGeneratorBytes, final Map<String, Object> resources,
            SequenceCodec<String> seqCodec) {
    this.featureGeneratorBytes = featureGeneratorBytes;
    this.resources = resources;
    this.seqCodec = seqCodec;
  }

  /*
   * Loads the default feature generator bytes via classpath resources.
   */
  private static byte[] loadDefaultFeatureGeneratorBytes() {

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    InputStream resource = TokenNameFinderFactory.class.getResourceAsStream(
            "/opennlp/tools/namefind/ner-default-features.xml");
    if (resource == null) {
      throw new IllegalStateException("Classpath must contain 'ner-default-features.xml' file!");
    }
    
    try (InputStream in = new BufferedInputStream(resource)) {
      byte[] buf = new byte[1024];
      int len;
      while ((len = in.read(buf)) > 0) {
        bytes.write(buf, 0, len);
      }
    }
    catch (IOException e) {
      throw new IllegalStateException("Failed reading from 'ner-default-features.xml' file on classpath!");
    }

    return bytes.toByteArray();
  }

  /**
   * @return Retrieves the {@link SequenceCodec} in use.
   */
  protected SequenceCodec<String> getSequenceCodec() {
    return seqCodec;
  }

  /**
   * @return Retrieves the additional {@code resources} in use.
   */
  protected Map<String, Object> getResources() {
    return resources;
  }

  /**
   * @return Retrieves {@code byte[]} in use representing the feature generator descriptor.
   */
  protected byte[] getFeatureGenerator() {
    return featureGeneratorBytes;
  }


  /**
   * Initializes a {@link TokenNameFinderFactory} instance via given parameters.
   *
   * @param subclassName The class name used for instantiation. If {@code null}, an
   *                     instance of {@link TokenNameFinderFactory} will be returned
   *                     per default. Otherwise, the {@link ExtensionLoader} mechanism
   *                     is applied to load the requested {@code subclassName}.
   * @param featureGeneratorBytes The {@code byte[]} representing the feature generator descriptor.
   * @param resources Additional resources in a mapping.
   * @param seqCodec The {@link SequenceCodec} to use.
   *
   * @return A valid {@link TokenNameFinderFactory} instance.
   * @throws InvalidFormatException Thrown if the {@link ExtensionLoader} mechanism failed to
   *                                create the factory associated with {@code subclassName}.
   */
  public static TokenNameFinderFactory create(String subclassName, byte[] featureGeneratorBytes,
      final Map<String, Object> resources, SequenceCodec<String> seqCodec)
      throws InvalidFormatException {
    TokenNameFinderFactory theFactory;
    if (subclassName == null) {
      // will create the default factory
      theFactory = new TokenNameFinderFactory();
    } else {
      try {
        theFactory = ExtensionLoader.instantiateExtension(
            TokenNameFinderFactory.class, subclassName);
      } catch (Exception e) {
        String msg = "Could not instantiate the " + subclassName
            + ". The initialization threw an exception.";
        throw new InvalidFormatException(msg, e);
      }
    }
    theFactory.init(featureGeneratorBytes, resources, seqCodec);
    return theFactory;
  }

  @Override
  public void validateArtifactMap() throws InvalidFormatException {
    // no additional artifacts
  }

  /**
   * @return Initializes and returns a {@link SequenceCodec} via its class name configured in a manifest.
   *         If that initialization fails (e.g., if no matching class could be loaded for the configured
   *         class name at runtime), the currently loaded (default) {@link SequenceCodec} is returned.
   *
   * @see BioCodec
   * @see BilouCodec
   */
  public SequenceCodec<String> createSequenceCodec() {

    if (artifactProvider != null) {
      String sequenceCodecImplName = artifactProvider.getManifestProperty(
          TokenNameFinderModel.SEQUENCE_CODEC_CLASS_NAME_PARAMETER);
      try {
        return instantiateSequenceCodec(sequenceCodecImplName);
      } catch (InvalidFormatException e) {
        // Uses the (already) available SequenceCodec instance. Default: BioCodec, see no-arg constructor
        return seqCodec;
      }
    }
    else {
      return seqCodec;
    }
  }

  /**
   * Creates and configures a new {@link NameContextGenerator} in a default combination.
   * 
   * @return A {@link NameContextGenerator} instance.
   *
   * @see DefaultNameContextGenerator
   * @see AdaptiveFeatureGenerator
   */
  public NameContextGenerator createContextGenerator() {

    AdaptiveFeatureGenerator featureGenerator = createFeatureGenerators();

    if (featureGenerator == null) {
      featureGenerator = new CachedFeatureGenerator(
          new WindowFeatureGenerator(new TokenFeatureGenerator(), 2, 2),
          new WindowFeatureGenerator(new TokenClassFeatureGenerator(true), 2, 2),
          new OutcomePriorFeatureGenerator(),
          new PreviousMapFeatureGenerator(),
          new BigramNameFeatureGenerator(),
          new SentenceFeatureGenerator(true, false));
    }

    return new DefaultNameContextGenerator(featureGenerator);
  }

  /**
   * Creates the {@link AdaptiveFeatureGenerator}. Usually this
   * is a set of generators contained in {@link AggregatedFeatureGenerator}.
   * <p>
   * Note:
   * The generators are created on every call to this method.
   *
   * @return The {@link AdaptiveFeatureGenerator} or {@code null} if there
   *         is no descriptor in the model.
   *
   * @throws FeatureGeneratorCreationError Thrown if configuration errors occurred.
   * @throws IllegalStateException Thrown if inconsistencies occurred during creation.
   */
  public AdaptiveFeatureGenerator createFeatureGenerators() {

    if (featureGeneratorBytes == null && artifactProvider != null) {
      featureGeneratorBytes = artifactProvider.getArtifact(
          TokenNameFinderModel.GENERATOR_DESCRIPTOR_ENTRY_NAME);
    }

    if (featureGeneratorBytes == null) {
      featureGeneratorBytes = loadDefaultFeatureGeneratorBytes();
    }

    InputStream descriptorIn = new ByteArrayInputStream(featureGeneratorBytes);

    AdaptiveFeatureGenerator generator;
    try {
      generator = GeneratorFactory.create(descriptorIn, key -> {
        if (artifactProvider != null) {
          return artifactProvider.getArtifact(key);
        }
        else {
          return resources.get(key);
        }
      });
    } catch (InvalidFormatException e) {
      // It is assumed that the creation of the feature generation does not
      // fail after it succeeded once during model loading.

      // But it might still be possible that such an exception is thrown,
      // in this case the caller should not be forced to handle the exception
      // and a Runtime Exception is thrown instead.

      // If the re-creation of the feature generation fails it is assumed
      // that this can only be caused by a programming mistake and therefore
      // throwing a Runtime Exception is reasonable

      throw new FeatureGeneratorCreationError(e);
    } catch (IOException e) {
      throw new IllegalStateException("Reading from mem cannot result in an I/O error", e);
    }

    return generator;
  }

  /**
   * Initializes a {@link SequenceCodec} instance via given parameters.
   *
   * @param sequenceCodecImplName The class name used for instantiation. If {@code null},
   *                              an instance of {@link BioCodec} will be returned
   *                              per default. Otherwise, the {@link ExtensionLoader}
   *                              mechanism is applied to load the requested {@code subclassName}.
   *
   * @return A valid {@link SequenceCodec} instance.
   * @throws InvalidFormatException Thrown if the {@link ExtensionLoader} mechanism failed to
   *                                create the codec associated with {@code sequenceCodecImplName}.
   * @see SequenceCodec
   * @see BioCodec
   * @see BilouCodec
   */
  public static SequenceCodec<String> instantiateSequenceCodec(String sequenceCodecImplName)
          throws InvalidFormatException {

    if (sequenceCodecImplName != null) {
      try {
        return ExtensionLoader.instantiateExtension(SequenceCodec.class, sequenceCodecImplName);
      } catch (Exception e) {
        String msg = "Could not instantiate the " + sequenceCodecImplName
                + ". The initialization threw an exception.";
        throw new InvalidFormatException(msg, e);
      }
    }
    else {
      // If nothing is specified return default codec!
      return new BioCodec();
    }
  }
}

