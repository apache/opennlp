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

package opennlp.tools.cmdline;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import opennlp.tools.util.InvalidFormatException;

/**
 * Loads a model and does all the error handling for the command line tools.
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 *
 * @param <T>
 */
public abstract class ModelLoader<T> {

  private final String modelName;

  protected ModelLoader(String modelName) {
    this.modelName = Objects.requireNonNull(modelName, "modelName must not be null!");
  }

  protected abstract T loadModel(InputStream modelIn) throws IOException;

  public T load(File modelFile) {

    long beginModelLoadingTime = System.currentTimeMillis();

    CmdLineUtil.checkInputFile(modelName + " model", modelFile);

    System.err.print("Loading " + modelName + " model ... ");

    T model;
    try (InputStream modelIn = new BufferedInputStream(
        CmdLineUtil.openInFile(modelFile), CmdLineUtil.IO_BUFFER_SIZE)) {
      model = loadModel(modelIn);
    }
    catch (InvalidFormatException e) {
      System.err.println("failed");
      throw new TerminateToolException(-1, "Model has invalid format", e);
    }
    catch (IOException e) {
      System.err.println("failed");
      throw new TerminateToolException(-1, "IO error while loading model file '" + modelFile + "'", e);
    }

    long modelLoadingDuration = System.currentTimeMillis() - beginModelLoadingTime;

    System.err.printf("done (%.3fs)\n", modelLoadingDuration / 1000d);

    return model;
  }
}
