/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.tools.ml.maxent.io;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

import opennlp.tools.ml.model.Context;

/**
 * A reader for GIS models stored in the format used in v1.0 of Maxent. It
 * extends the PlainTextGISModelReader to read in the info and then overrides
 * the getParameters method so that it can appropriately read the binary file
 * which stores the parameters.
 */
public class OldFormatGISModelReader extends PlainTextGISModelReader {
  private DataInputStream paramsInput;

  /**
   * Constructor which takes the name of the model without any suffixes, such as
   * ".mei.gz" or ".mep.gz".
   */
  public OldFormatGISModelReader(String modelname) throws IOException {
    super(new File(modelname + ".mei.gz"));
    paramsInput = new DataInputStream(new GZIPInputStream(new FileInputStream(
        modelname + ".mep.gz")));
  }

  /**
   * Reads the parameters from a file and populates an array of context objects.
   *
   * @param outcomePatterns
   *          The outcomes patterns for the model. The first index refers to
   *          which outcome pattern (a set of outcomes that occurs with a
   *          context) is being specified. The second index specifies the number
   *          of contexts which use this pattern at index 0, and the index of
   *          each outcomes which make up this pattern in indicies 1-n.
   * @return An array of context objects.
   * @throws java.io.IOException
   *           when the model file does not match the outcome patterns or can
   *           not be read.
   */
  protected Context[] getParameters(int[][] outcomePatterns)
      throws java.io.IOException {
    Context[] params = new Context[NUM_PREDS];
    int pid = 0;
    for (int i = 0; i < outcomePatterns.length; i++) {
      // construct outcome pattern
      int[] outcomePattern = new int[outcomePatterns[i].length - 1];
      System.arraycopy(outcomePatterns[i], 1, outcomePattern, 0, outcomePatterns[i].length - 1);
      // populate parameters for each context which uses this outcome pattern.
      for (int j = 0; j < outcomePatterns[i][0]; j++) {
        double[] contextParameters = new double[outcomePatterns[i].length - 1];
        for (int k = 1; k < outcomePatterns[i].length; k++) {
          contextParameters[k - 1] = readDouble();
        }
        params[pid] = new Context(outcomePattern, contextParameters);
        pid++;
      }
    }
    return params;
  }
}
