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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import opennlp.tools.tokenize.BPETokenizer.SymbolPair;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.ArtifactSerializer;

/**
 * A {@link BaseToolFactory} for BPE tokenization that manages
 * the BPE merge rules artifact and its serialization within a
 * {@link BPEModel}.
 * <p>
 * This factory is responsible for:
 * <ul>
 *   <li>Providing the {@link BPEMergesSerializer} that reads
 *       and writes BPE merge rules as a text-based artifact
 *       ({@code bpe.merges}) inside the model ZIP package.
 *   </li>
 *   <li>Supplying the merge rules to the {@link BPEModel}
 *       via {@link #createArtifactMap()}.</li>
 *   <li>Validating that a loaded model contains valid merge
 *       rules.</li>
 * </ul>
 * <p>
 * This class is typically not used directly. It is
 * instantiated internally by {@link BPETokenizerTrainer}
 * during training and by {@link BPEModel} during model
 * loading.
 *
 * @see BPEModel
 * @see BPETokenizer
 * @see BPETokenizerTrainer
 */
public class BPETokenizerFactory extends BaseToolFactory {

  /** The artifact entry name for BPE merge rules. */
  static final String MERGES_ENTRY_NAME = "bpe.merges";

  /** The ISO language code. */
  private String languageCode;
  /** The ordered list of BPE merge operations. */
  private List<SymbolPair> merges;

  /**
   * Creates a {@link BPETokenizerFactory}.
   * Required empty constructor for model loading.
   */
  public BPETokenizerFactory() {
  }

  /**
   * Creates a {@link BPETokenizerFactory} with the given
   * parameters.
   *
   * @param langCode The ISO language code.
   *                 Must not be {@code null}.
   * @param mergeOps The ordered list of BPE merge operations.
   *                 Must not be {@code null}.
   */
  public BPETokenizerFactory(final String langCode,
                              final List<SymbolPair> mergeOps) {
    this.languageCode = Objects.requireNonNull(
        langCode, "languageCode must not be null");
    this.merges = Objects.requireNonNull(
        mergeOps, "merges must not be null");
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, ArtifactSerializer<?>>
      createArtifactSerializersMap() {
    Map<String, ArtifactSerializer<?>> serializers =
        super.createArtifactSerializersMap();
    serializers.put("merges", new BPEMergesSerializer());
    return serializers;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> createArtifactMap() {
    Map<String, Object> artifactMap = super.createArtifactMap();
    if (merges != null) {
      artifactMap.put(MERGES_ENTRY_NAME, new ArrayList<>(merges));
    }
    return artifactMap;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, String> createManifestEntries() {
    Map<String, String> entries = super.createManifestEntries();
    return entries;
  }

  /** {@inheritDoc} */
  @Override
  public void validateArtifactMap() throws InvalidFormatException {
    Object mergesArtifact =
        this.artifactProvider.getArtifact(MERGES_ENTRY_NAME);
    if (!(mergesArtifact instanceof List<?>)) {
      throw new InvalidFormatException(
          "Missing or invalid BPE merges artifact!");
    }
  }

  /**
   * @return The ISO language code for this factory.
   */
  public String getLanguageCode() {
    return languageCode;
  }

  /**
   * Retrieves the BPE merge rules from the loaded model artifacts.
   *
   * @return The ordered list of {@link SymbolPair} merge operations.
   */
  @SuppressWarnings("unchecked")
  public List<SymbolPair> getMerges() {
    if (merges != null) {
      return merges;
    }
    return (List<SymbolPair>)
        this.artifactProvider.getArtifact(MERGES_ENTRY_NAME);
  }

  /**
   * An {@link ArtifactSerializer} for BPE merge rules.
   * <p>
   * Serializes merge rules as a text file with one merge pair per line,
   * in the format: {@code left right}.
   */
  static class BPEMergesSerializer
      implements ArtifactSerializer<List<SymbolPair>> {

    @Override
    public List<SymbolPair> create(final InputStream in)
        throws IOException {
      final List<SymbolPair> merges = new ArrayList<>();
      final BufferedReader reader = new BufferedReader(
          new InputStreamReader(in, StandardCharsets.UTF_8));
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }
        final int space = line.indexOf(' ');
        if (space < 0) {
          throw new IOException(
              "Invalid BPE merge line (expected "
              + "'left right'): " + line);
        }
        merges.add(new SymbolPair(
            line.substring(0, space),
            line.substring(space + 1)));
      }
      return merges;
    }

    @Override
    public void serialize(final List<SymbolPair> artifact,
                          final OutputStream out)
        throws IOException {
      final BufferedWriter writer = new BufferedWriter(
          new OutputStreamWriter(out, StandardCharsets.UTF_8));
      for (final SymbolPair merge : artifact) {
        writer.write(merge.left());
        writer.write(' ');
        writer.write(merge.right());
        writer.newLine();
      }
      writer.flush();
    }
  }
}
