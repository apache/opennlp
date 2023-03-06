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

package opennlp.tools.ml.maxent.io;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.ml.maxent.quasinewton.QNModel;
import opennlp.tools.ml.model.Context;
import opennlp.tools.ml.model.DataReader;

/**
 * The base class for readers of {@link QNModel QN models}.
 *
 * @see QNModel
 * @see GISModelReader
 */
public class QNModelReader extends GISModelReader {

  private static final Logger logger = LoggerFactory.getLogger(QNModelReader.class);

  /**
   * Initializes a {@link QNModelReader} via a {@link DataReader}.
   *
   * @param dataReader The {@link DataReader} that references the model to be read.
   */
  public QNModelReader(DataReader dataReader) {
    super(dataReader);
  }

  /**
   * Initializes a {@link QNModelReader} via a {@link File}.
   *
   * @param file The {@link File} that references the model to be read.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public QNModelReader(File file) throws IOException {
    super(file);
  }

  @Override
  public void checkModelType() throws IOException {
    String modelType = readUTF();
    if (!modelType.equals("QN"))
      logger.error("Attempting to load a {}"
              + " model as a MAXENT_QN model. You should expect problems.", modelType);
  }

  /**
   * Retrieves a model from disk.
   *
   * <p>
   * If you are creating a reader for a format which won't work with this
   * (perhaps a database or xml file), override this method and ignore the other
   * methods provided in this abstract class.
   *
   * @return The {@link QNModel} stored in the format and location specified to this
   *         {@link QNModelReader} (usually via its constructor).
   */
  @Override
  public QNModel constructModel() throws IOException {
    String[] outcomeLabels = getOutcomes();
    int[][] outcomePatterns = getOutcomePatterns();
    String[] predLabels = getPredicates();
    Context[] params = getParameters(outcomePatterns);

    return new QNModel(params, predLabels, outcomeLabels);
  }
}
