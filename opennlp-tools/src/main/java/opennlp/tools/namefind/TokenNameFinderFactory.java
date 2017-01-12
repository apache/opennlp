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

// Idea of this factory is that most resources/impls used by the name finder
// can be modified through this class!
// That only works if that's the central class used for training/runtime

public class TokenNameFinderFactory extends BaseToolFactory {

  private byte[] featureGeneratorBytes;
  private Map<String, Object> resources;
  private SequenceCodec<String> seqCodec;

  /**
   * Creates a {@link TokenNameFinderFactory} that provides the default implementation
   * of the resources.
   */
  public TokenNameFinderFactory() {
    this.seqCodec = new BioCodec();
  }

  public TokenNameFinderFactory(byte[] featureGeneratorBytes, final Map<String, Object> resources,
      SequenceCodec<String> seqCodec) {
    init(featureGeneratorBytes, resources, seqCodec);
  }

  void init(byte[] featureGeneratorBytes, final Map<String, Object> resources,
      SequenceCodec<String> seqCodec) {
    this.featureGeneratorBytes = featureGeneratorBytes;
    this.resources = resources;
    this.seqCodec = seqCodec;
  }

  private static byte[] loadDefaultFeatureGeneratorBytes() {

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (InputStream in = TokenNameFinderFactory.class.getResourceAsStream(
        "/opennlp/tools/namefind/ner-default-features.xml")) {

      if (in == null) {
        throw new IllegalStateException("Classpath must contain ner-default-features.xml file!");
      }

      byte buf[] = new byte[1024];
      int len;
      while ((len = in.read(buf)) > 0) {
        bytes.write(buf, 0, len);
      }
    }
    catch (IOException e) {
      throw new IllegalStateException("Failed reading from ner-default-features.xml file on classpath!");
    }

    return bytes.toByteArray();
  }

  protected SequenceCodec<String> getSequenceCodec() {
    return seqCodec;
  }

  protected Map<String, Object> getResources() {
    return resources;
  }

  protected byte[] getFeatureGenerator() {
    return featureGeneratorBytes;
  }

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
            + ". The initialization throw an exception.";
        System.err.println(msg);
        e.printStackTrace();
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

  public SequenceCodec<String> createSequenceCodec() {

    if (artifactProvider != null) {
      String sequeceCodecImplName = artifactProvider.getManifestProperty(
          TokenNameFinderModel.SEQUENCE_CODEC_CLASS_NAME_PARAMETER);
      return instantiateSequenceCodec(sequeceCodecImplName);
    }
    else {
      return seqCodec;
    }
  }

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
   * is a set of generators contained in the {@link AggregatedFeatureGenerator}.
   *
   * Note:
   * The generators are created on every call to this method.
   *
   * @return the feature generator or null if there is no descriptor in the model
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

  public static SequenceCodec<String> instantiateSequenceCodec(
      String sequenceCodecImplName) {

    if (sequenceCodecImplName != null) {
      return ExtensionLoader.instantiateExtension(
          SequenceCodec.class, sequenceCodecImplName);
    }
    else {
      // If nothing is specified return old default!
      return new BioCodec();
    }
  }
}

