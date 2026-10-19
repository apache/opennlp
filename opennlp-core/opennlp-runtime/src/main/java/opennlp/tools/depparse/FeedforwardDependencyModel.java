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

package opennlp.tools.depparse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.nio.file.Path;
import java.util.Map;

import opennlp.tools.ml.BeamSearch;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ParamChecks;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.BaseModel;

/**
 * The persisted form of a trained {@link FeedforwardDependencyParser}: the network
 * weights plus the standard model manifest, serialized and loaded through the same
 * {@link BaseModel} machinery as every other tool model.
 *
 * <p>The weights are not modified after construction or loading. A model can be
 * shared between threads and between parsers; {@link FeedforwardDependencyTrainer#refine
 * refine} trains a copy and returns a new model.</p>
 *
 * @see FeedforwardDependencyParser
 * @see FeedforwardDependencyTrainer
 * @since 3.0.0
 */
public class FeedforwardDependencyModel extends BaseModel {

  @Serial
  private static final long serialVersionUID = -5262211380538922987L;

  /** The component name recorded in the model manifest. */
  private static final String COMPONENT_NAME = "FeedforwardDependencyParser";

  /** The artifact map entry holding the network. */
  static final String NETWORK_ENTRY_NAME = "feedforward.network";

  /** The artifact extension the network serializer is registered under. */
  private static final String NETWORK_EXTENSION = "network";

  /** The beam size of a model that was not refined with beam search. */
  private static final int GREEDY_BEAM_SIZE = 1;

  /**
   * Initializes a {@link FeedforwardDependencyModel} from a trained network.
   *
   * @param languageCode The ISO language code of the training data. Must not be
   *                     {@code null}.
   * @param network The trained network. Must not be {@code null}.
   * @param manifestInfoEntries Additional entries for the manifest, or {@code null}.
   * @throws IllegalArgumentException Thrown if {@code languageCode} or {@code network}
   *         is {@code null}.
   */
  FeedforwardDependencyModel(String languageCode, FeedforwardNetwork network,
      Map<String, String> manifestInfoEntries) {
    super(COMPONENT_NAME, ParamChecks.requireNonNullArg(languageCode, "languageCode"),
        manifestInfoEntries);
    ParamChecks.requireNonNullArg(network, "network");
    artifactMap.put(NETWORK_ENTRY_NAME, network);
    checkArtifactMap();
  }

  /**
   * Initializes a {@link FeedforwardDependencyModel} from a serialized model.
   *
   * @param in The stream to read the model from. Must not be {@code null}.
   * @throws IOException Thrown if reading fails or the content is not a valid model.
   * @throws IllegalArgumentException Thrown if {@code in} is {@code null}, or if it holds
   *     the model package of another component; the cause is an
   *     {@link InvalidFormatException}.
   */
  public FeedforwardDependencyModel(InputStream in) throws IOException {
    super(COMPONENT_NAME, ParamChecks.requireNonNullArg(in, "in"));
  }

  /**
   * Initializes a {@link FeedforwardDependencyModel} from a serialized model file.
   *
   * @param modelFile The file to read the model from. Must not be {@code null}.
   * @throws IOException Thrown if reading fails or the content is not a valid model.
   * @throws IllegalArgumentException Thrown if {@code modelFile} is {@code null}, or if it holds
   *     the model package of another component; the cause is an
   *     {@link InvalidFormatException}.
   */
  public FeedforwardDependencyModel(File modelFile) throws IOException {
    super(COMPONENT_NAME, ParamChecks.requireNonNullArg(modelFile, "modelFile"));
  }

  /**
   * Initializes a {@link FeedforwardDependencyModel} from a serialized model file.
   *
   * @param modelPath The path to read the model from. Must not be {@code null}.
   * @throws IOException Thrown if reading fails or the content is not a valid model.
   * @throws IllegalArgumentException Thrown if {@code modelPath} is {@code null}, or if it holds
   *     the model package of another component; the cause is an
   *     {@link InvalidFormatException}.
   */
  public FeedforwardDependencyModel(Path modelPath) throws IOException {
    super(COMPONENT_NAME, ParamChecks.requireNonNullArg(modelPath, "modelPath"));
  }

  /**
   * {@inheritDoc}
   *
   * <p>Registers the network serializer.</p>
   */
  @Override
  protected void createArtifactSerializers(Map<String, ArtifactSerializer<?>> serializers) {
    super.createArtifactSerializers(serializers);
    serializers.put(NETWORK_EXTENSION, new FeedforwardNetwork.Serializer());
  }

  /**
   * {@inheritDoc}
   *
   * @throws InvalidFormatException Thrown if the network artifact is missing or not a
   *         feedforward network, or the manifest records a beam size that is not a
   *         positive integer.
   */
  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();
    if (!(artifactMap.get(NETWORK_ENTRY_NAME) instanceof FeedforwardNetwork)) {
      throw new InvalidFormatException("The " + NETWORK_ENTRY_NAME
          + " artifact is missing or not a feedforward network.");
    }
    final String beamSize = getManifestProperty(BeamSearch.BEAM_SIZE_PARAMETER);
    if (beamSize != null && !isPositiveInteger(beamSize)) {
      throw new InvalidFormatException("The manifest " + BeamSearch.BEAM_SIZE_PARAMETER
          + " must be a positive integer: " + beamSize);
    }
  }

  /**
   * Returns the beam size a parser uses when none is given: the beam size the model was
   * refined with, which the manifest records under
   * {@value BeamSearch#BEAM_SIZE_PARAMETER}, or {@code 1} (greedy) for a model that was
   * not refined.
   *
   * @return The beam size. Always positive.
   */
  public int getBeamSize() {
    final String beamSize = getManifestProperty(BeamSearch.BEAM_SIZE_PARAMETER);
    return beamSize == null ? GREEDY_BEAM_SIZE : Integer.parseInt(beamSize);
  }

  /**
   * Tells whether a manifest value is a positive decimal integer.
   *
   * @param value The manifest value. Must not be {@code null}.
   * @return {@code true} if {@code value} parses as an {@code int} above zero.
   */
  private static boolean isPositiveInteger(String value) {
    try {
      return Integer.parseInt(value) > 0;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * @return The network. Never {@code null}.
   */
  FeedforwardNetwork network() {
    return (FeedforwardNetwork) artifactMap.get(NETWORK_ENTRY_NAME);
  }
}
