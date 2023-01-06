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

package opennlp.tools.util.model;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import opennlp.tools.commons.Internal;
import opennlp.tools.ml.maxent.GISTrainer;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.GenericModelWriter;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.TrainingParameters;

/**
 * Utility class for handling of {@link MaxentModel models}.
 */
public final class ModelUtil {

  private ModelUtil() {
    // not intended to be instantiated
  }

  /**
   * Writes the given {@link MaxentModel} to the specified {@link OutputStream}.
   * <p>
   * <b>Note:</b>
   * The provided stream is not closed.
   *
   * @param model The {@link MaxentModel model} to be written.
   * @param out the {@link OutputStream stream} to be used for writing.
   *
   * @throws IOException Thrown if IO errors occurred.
   * @throws IllegalArgumentException Thrown if one of the parameters is {@code null}.
   */
  public static void writeModel(MaxentModel model, final OutputStream out)
          throws IOException, IllegalArgumentException {

    Objects.requireNonNull(model, "model parameter must not be null");
    Objects.requireNonNull(out, "out parameter must not be null");

    GenericModelWriter modelWriter = new GenericModelWriter((AbstractModel) model,
        new DataOutputStream(new OutputStream() {
          @Override
          public void write(int b) throws IOException {
            out.write(b);
          }
        }));

    modelWriter.persist();
  }

  /**
   * Checks if the {@code expectedOutcomes} are all contained as outcomes in the
   * given {@link MaxentModel model}.
   *
   * @param model A valid {@link MaxentModel} instance.
   * @param expectedOutcomes The outcomes to be checked for.
   *
   * @return {@code true} if all expected outcomes are the only outcomes of the model
   *         {@code false} otherwise.
   */
  public static boolean validateOutcomes(MaxentModel model, String... expectedOutcomes) {

    boolean result = true;

    if (expectedOutcomes.length == model.getNumOutcomes()) {

      Set<String> expectedOutcomesSet = new HashSet<>(Arrays.asList(expectedOutcomes));

      for (int i = 0; i < model.getNumOutcomes(); i++) {
        if (!expectedOutcomesSet.contains(model.getOutcome(i))) {
          result = false;
          break;
        }
      }
    }
    else {
      result = false;
    }

    return result;
  }

  /**
   * Reads from the provided {@link InputStream} into a byte array.
   *
   * @param in A valid, open {@link InputStream} to read data from.
   *
   * @return A {@code byte[]} with the data read.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public static byte[] read(InputStream in) throws IOException {
    ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();

    int length;
    byte[] buffer = new byte[1024];
    while ((length = in.read(buffer)) > 0) {
      byteArrayOut.write(buffer, 0, length);
    }
    byteArrayOut.close();

    return byteArrayOut.toByteArray();
  }

  /**
   * Adds {@code cutoff} and {@code iterations} to {@code manifestInfoEntries}.
   *
   * @param manifestInfoEntries A {@link Map} representing a {@code manifest.properties} config.
   * @param cutoff The cut-off value to set. Must be greater than {@code 0}.
   * @param iterations The number of iterations to set. Must be greater than {@code 0}.
   */
  public static void addCutoffAndIterations(Map<String, String> manifestInfoEntries,
      int cutoff, int iterations) {
    manifestInfoEntries.put(BaseModel.TRAINING_CUTOFF_PROPERTY, Integer.toString(cutoff));
    manifestInfoEntries.put(BaseModel.TRAINING_ITERATIONS_PROPERTY, Integer.toString(iterations));
  }

  /**
   * Creates the default {@link TrainingParameters} in case they are not provided.
   * <p>
   * <b>Note:</b>
   * Do not use this method, internal use only!
   *
   * @return The {@link TrainingParameters} instance with default configuration.
   */
  @Internal
  public static TrainingParameters createDefaultTrainingParameters() {
    TrainingParameters mlParams = new TrainingParameters();
    mlParams.put(TrainingParameters.ALGORITHM_PARAM, GISTrainer.MAXENT_VALUE);
    mlParams.put(TrainingParameters.ITERATIONS_PARAM, 100);
    mlParams.put(TrainingParameters.CUTOFF_PARAM, 5);

    return mlParams;
  }
}
