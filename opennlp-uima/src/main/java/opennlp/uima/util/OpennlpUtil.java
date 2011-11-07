/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

package opennlp.uima.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import opennlp.maxent.GISModel;
import opennlp.tools.util.model.BaseModel;

/**
 * This class contains utils methods for the maxent library.
 */
final public class OpennlpUtil {
  private OpennlpUtil() {
    // this is util class must not be instantiated
  }

  /**
   * Serializes a {@link GISModel} and writes it to the given
   * {@link OutputStream}.
   * 
   * @param model
   * @param out
   * @throws IOException
   */
  public static void serialize(BaseModel model, File modelFile)
      throws IOException {
    OutputStream modelOut = null;

    try {
      modelOut = new BufferedOutputStream(new FileOutputStream(modelFile));
      model.serialize(modelOut);
    } finally {
      if (modelOut != null)
        modelOut.close();
    }
  }
}