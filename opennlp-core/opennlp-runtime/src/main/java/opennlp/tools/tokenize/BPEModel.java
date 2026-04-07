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

package opennlp.tools.tokenize;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import opennlp.tools.tokenize.BPETokenizer.SymbolPair;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.BaseModel;

/**
 * The {@link BPEModel} stores learned BPE merge operations and can be
 * serialized and deserialized for reuse.
 * <p>
 * A model is created by the {@link BPETokenizerTrainer} and contains an ordered
 * list of {@link BPETokenizer.SymbolPair} merge operations that define the BPE
 * vocabulary. The model is persisted as a standard OpenNLP ZIP package with a
 * {@code bpe.merges} artifact containing the merge rules.
 * <p>
 * <b>Usage:</b>
 * <pre>{@code
 * // Create via training
 * BPETokenizerTrainer trainer = new BPETokenizerTrainer();
 * BPEModel model = trainer.train(corpus, 10000, "en");
 *
 * // Save to disk
 * model.serialize(Path.of("bpe-en.bin"));
 *
 * // Load from disk
 * BPEModel loaded = new BPEModel(Path.of("bpe-en.bin"));
 *
 * // Use for tokenization
 * BPETokenizer tokenizer = new BPETokenizer(loaded);
 * }</pre>
 *
 * @see BPETokenizer
 * @see BPETokenizerTrainer
 * @see BPETokenizerFactory
 */
public final class BPEModel extends BaseModel {

  private static final long serialVersionUID = 1L;
  /** The component name for this model type. */
  private static final String COMPONENT_NAME = "BPETokenizer";

  /**
   * Creates a {@link BPEModel} from trained merge rules.
   *
   * @param merges             The ordered list of merge operations.
   *                           Must not be {@code null}.
   * @param manifestInfoEntries Additional manifest info.
   * @param factory            The {@link BPETokenizerFactory}.
   */
  public BPEModel(final List<SymbolPair> merges,
                  final Map<String, String> manifestInfoEntries,
                  final BPETokenizerFactory factory) {
    super(COMPONENT_NAME,
        factory.getLanguageCode(),
        manifestInfoEntries, factory);
    artifactMap.put(BPETokenizerFactory.MERGES_ENTRY_NAME,
        new ArrayList<>(merges));
    checkArtifactMap();
  }

  /**
   * Initializes a {@link BPEModel} from an {@link InputStream}.
   *
   * @param in The {@link InputStream} for loading the model.
   * @throws IOException Thrown if IO errors occurred.
   */
  public BPEModel(final InputStream in) throws IOException {
    super(COMPONENT_NAME, in);
  }

  /**
   * Initializes a {@link BPEModel} from a {@link File}.
   *
   * @param modelFile The {@link File} for loading the model.
   * @throws IOException Thrown if IO errors occurred.
   */
  public BPEModel(final File modelFile) throws IOException {
    super(COMPONENT_NAME, modelFile);
  }

  /**
   * Initializes a {@link BPEModel} from a {@link Path}.
   *
   * @param modelPath The {@link Path} for loading the model.
   * @throws IOException Thrown if IO errors occurred.
   */
  public BPEModel(final Path modelPath) throws IOException {
    super(COMPONENT_NAME, modelPath);
  }

  /**
   * Initializes a {@link BPEModel} from a {@link URL}.
   *
   * @param modelURL The {@link URL} for loading the model.
   * @throws IOException Thrown if IO errors occurred.
   */
  public BPEModel(final URL modelURL) throws IOException {
    super(COMPONENT_NAME, modelURL);
  }

  @Override
  protected void validateArtifactMap()
      throws InvalidFormatException {
    super.validateArtifactMap();

    Object mergesArtifact =
        artifactMap.get(BPETokenizerFactory.MERGES_ENTRY_NAME);
    if (!(mergesArtifact instanceof List<?>)) {
      throw new InvalidFormatException(
          "BPE model is incomplete: missing merge rules!");
    }
  }

  @Override
  protected Class<? extends BaseToolFactory> getDefaultFactory() {
    return BPETokenizerFactory.class;
  }

  /**
   * @return The active {@link BPETokenizerFactory}.
   */
  public BPETokenizerFactory getFactory() {
    return (BPETokenizerFactory) this.toolFactory;
  }

  /**
   * @return An unmodifiable, ordered list of BPE merge operations stored in this model.
   */
  @SuppressWarnings("unchecked")
  public List<SymbolPair> getMerges() {
    return Collections.unmodifiableList(
        (List<SymbolPair>) artifactMap.get(BPETokenizerFactory.MERGES_ENTRY_NAME));
  }
}
