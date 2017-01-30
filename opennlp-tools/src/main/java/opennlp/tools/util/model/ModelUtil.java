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

import opennlp.tools.ml.maxent.GISTrainer;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.GenericModelWriter;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.TrainingParameters;

/**
 * Utility class for handling of {@link MaxentModel}s.
 */
public final class ModelUtil {

  private ModelUtil() {
    // not intended to be instantiated
  }

  /**
   * Writes the given model to the given {@link OutputStream}.
   *
   * This methods does not closes the provided stream.
   *
   * @param model the model to be written
   * @param out the stream the model should be written to
   *
   * @throws IOException
   * @throws IllegalArgumentException in case one of the parameters is null
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
   * Checks if the expected outcomes are all contained as outcomes in the given model.
   *
   * @param model
   * @param expectedOutcomes
   *
   * @return true if all expected outcomes are the only outcomes of the model.
   */
  public static boolean validateOutcomes(MaxentModel model, String... expectedOutcomes) {

    boolean result = true;

    if (expectedOutcomes.length == model.getNumOutcomes()) {

      Set<String> expectedOutcomesSet = new HashSet<>();
      expectedOutcomesSet.addAll(Arrays.asList(expectedOutcomes));

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
   * Writes the provided {@link InputStream} into a byte array
   * which is returned
   *
   * @param in stream to read data for the byte array from
   * @return byte array with the contents of the stream
   *
   * @throws IOException if an exception is thrown while reading
   *     from the provided {@link InputStream}
   */
  public static byte[] read(InputStream in) throws IOException {
    ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();

    int length;
    byte buffer[] = new byte[1024];
    while ((length = in.read(buffer)) > 0) {
      byteArrayOut.write(buffer, 0, length);
    }
    byteArrayOut.close();

    return byteArrayOut.toByteArray();
  }

  public static void addCutoffAndIterations(Map<String, String> manifestInfoEntries,
      int cutoff, int iterations) {
    manifestInfoEntries.put(BaseModel.TRAINING_CUTOFF_PROPERTY, Integer.toString(cutoff));
    manifestInfoEntries.put(BaseModel.TRAINING_ITERATIONS_PROPERTY, Integer.toString(iterations));
  }

  /**
   * Creates the default training parameters in case they are not provided.
   *
   * Note: Do not use this method, internal use only!
   *
   *
   * @return training parameters instance
   */
  public static TrainingParameters createDefaultTrainingParameters() {
    TrainingParameters mlParams = new TrainingParameters();
    mlParams.put(TrainingParameters.ALGORITHM_PARAM, GISTrainer.MAXENT_VALUE);
    mlParams.put(TrainingParameters.ITERATIONS_PARAM, Integer.toString(100));
    mlParams.put(TrainingParameters.CUTOFF_PARAM, Integer.toString(5));

    return mlParams;
  }
}
