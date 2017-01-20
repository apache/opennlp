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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.BaseModel;

/**
 * Util class for the command line interface.
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 */
public final class CmdLineUtil {

  static final int IO_BUFFER_SIZE = 1024 * 1024;

  private CmdLineUtil() {
    // not intended to be instantiated
  }

  /**
   * Check that the given input file is valid.
   * <p>
   * To pass the test it must:<br>
   * - exist<br>
   * - not be a directory<br>
   * - accessibly<br>
   *
   * @param name the name which is used to refer to the file in an error message, it
   *     should start with a capital letter.
   *
   * @param inFile the particular file to check to qualify an input file
   *
   * @throws TerminateToolException  if test does not pass this exception is
   *     thrown and an error message is printed to the console.
   */
  public static void checkInputFile(String name, File inFile) {

    String isFailure = null;

    if (inFile.isDirectory()) {
      isFailure = "The " + name + " file is a directory!";
    }
    else if (!inFile.exists()) {
      isFailure = "The " + name + " file does not exist!";
    }
    else if (!inFile.canRead()) {
      isFailure = "No permissions to read the " + name + " file!";
    }

    if (null != isFailure) {
      throw new TerminateToolException(-1, isFailure + " Path: " + inFile.getAbsolutePath());
    }
  }

  /**
   * Tries to ensure that it is possible to write to an output file.
   * <p>
   * The method does nothing if it is possible to write otherwise
   * it prints an appropriate error message and a {@link TerminateToolException} is thrown.
   * <p>
   * Computing the contents of an output file (e.g. ME model) can be very time consuming.
   * Prior to this computation it should be checked once that writing this output file is
   * possible to be able to fail fast if not. If this validation is only done after a time
   * consuming computation it could frustrate the user.
   *
   * @param name human-friendly file name. for example perceptron model
   * @param outFile file
   */
  public static void checkOutputFile(String name, File outFile) {

    String isFailure = null;

    if (outFile.exists()) {

      // The file already exists, ensure that it is a normal file and that it is
      // possible to write into it

      if (outFile.isDirectory()) {
        isFailure = "The " + name + " file is a directory!";
      }
      else if (outFile.isFile()) {
        if (!outFile.canWrite()) {
          isFailure = "No permissions to write the " + name + " file!";
        }
      }
      else {
        isFailure = "The " + name + " file is not a normal file!";
      }
    }
    else {

      // The file does not exist ensure its parent
      // directory exists and has write permissions to create
      // a new file in it

      File parentDir = outFile.getAbsoluteFile().getParentFile();

      if (parentDir != null && parentDir.exists()) {

        if (!parentDir.canWrite()) {
          isFailure = "No permissions to create the " + name + " file!";
        }
      }
      else {
        isFailure = "The parent directory of the " + name + " file does not exist, " +
            "please create it first!";
      }

    }

    if (null != isFailure) {
      throw new TerminateToolException(-1, isFailure + " Path: " + outFile.getAbsolutePath());
    }
  }

  public static FileInputStream openInFile(File file) {
    try {
      return new FileInputStream(file);
    } catch (FileNotFoundException e) {
      throw new TerminateToolException(-1, "File '" + file + "' cannot be found", e);
    }
  }

  public static InputStreamFactory createInputStreamFactory(File file) {
    try {
      return new MarkableFileInputStreamFactory(file);
    } catch (FileNotFoundException e) {
      throw new TerminateToolException(-1, "File '" + file + "' cannot be found", e);
    }
  }

  /**
   * Writes a {@link BaseModel} to disk. Occurring errors are printed to the console
   * to inform the user.
   *
   * @param modelName type of the model, name is used in error messages.
   * @param modelFile output file of the model
   * @param model the model itself which should be written to disk
   */
  public static void writeModel(String modelName, File modelFile, BaseModel model) {

    CmdLineUtil.checkOutputFile(modelName + " model", modelFile);

    System.err.print("Writing " + modelName + " model ... ");

    long beginModelWritingTime = System.currentTimeMillis();

    try (OutputStream modelOut = new BufferedOutputStream(
        new FileOutputStream(modelFile), IO_BUFFER_SIZE)) {
      model.serialize(modelOut);
    } catch (IOException e) {
      System.err.println("failed");
      throw new TerminateToolException(-1, "Error during writing model file '" + modelFile + "'", e);
    }

    long modelWritingDuration = System.currentTimeMillis() - beginModelWritingTime;

    System.err.printf("done (%.3fs)\n", modelWritingDuration / 1000d);

    System.err.println();

    System.err.println("Wrote " + modelName + " model to");
    System.err.println("path: " + modelFile.getAbsolutePath());

    System.err.println();
  }

  /**
   * Returns the index of the parameter in the arguments, or -1 if the parameter is not found.
   *
   * @param param parameter name
   * @param args arguments
   * @return the index of the parameter in the arguments, or -1 if the parameter is not found
   */
  public static int getParameterIndex(String param, String args[]) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].startsWith("-") && args[i].equals(param)) {
        return i;
      }
    }

    return -1;
  }

  /**
   * Retrieves the specified parameter from the given arguments.
   *
   * @param param parameter name
   * @param args arguments
   * @return parameter value
   */
  public static String getParameter(String param, String args[]) {
    int i = getParameterIndex(param, args);
    if (-1 < i) {
      i++;
      if (i < args.length) {
        return args[i];
      }
    }

    return null;
  }

  /**
   * Retrieves the specified parameter from the specified arguments.
   *
   * @param param parameter name
   * @param args arguments
   * @return parameter value
   */
  public static Integer getIntParameter(String param, String args[]) {
    String value = getParameter(param, args);

    try {
      if (value != null)
        return Integer.parseInt(value);
    }
    catch (NumberFormatException ignored) {
      // in this case return null
    }

    return null;
  }

  /**
   * Retrieves the specified parameter from the specified arguments.
   *
   * @param param parameter name
   * @param args arguments
   * @return parameter value
   */
  public static Double getDoubleParameter(String param, String args[]) {
    String value = getParameter(param, args);

    try {
      if (value != null)
        return Double.parseDouble(value);
    }
    catch (NumberFormatException ignored) {
      // in this case return null
    }

    return null;
  }

  public static void checkLanguageCode(String code) {
    List<String> languageCodes  = new ArrayList<>();
    languageCodes.addAll(Arrays.asList(Locale.getISOLanguages()));
    languageCodes.add("x-unspecified");

    if (!languageCodes.contains(code)) {
      throw new TerminateToolException(1, "Unknown language code " + code + ", " +
          "must be an ISO 639 code!");
    }
  }

  public static boolean containsParam(String param, String args[]) {
    for (String arg : args) {
      if (arg.equals(param)) {
        return true;
      }
    }

    return false;
  }

  public static void handleStdinIoError(IOException e) {
    throw new TerminateToolException(-1, "IO Error while reading from stdin: " + e.getMessage(), e);
  }

  public static TerminateToolException createObjectStreamError(IOException e) {
    return new TerminateToolException(-1, "IO Error while creating an Input Stream: " + e.getMessage(), e);
  }

  public static void handleCreateObjectStreamError(IOException e) {
    throw createObjectStreamError(e);
  }

  // its optional, passing null is allowed
  public static TrainingParameters loadTrainingParameters(String paramFile,
      boolean supportSequenceTraining) {

    TrainingParameters params = null;

    if (paramFile != null) {

      checkInputFile("Training Parameter", new File(paramFile));

      try (InputStream paramsIn  = new FileInputStream(new File(paramFile))) {
        params = new opennlp.tools.util.TrainingParameters(paramsIn);
      } catch (IOException e) {
        throw new TerminateToolException(-1, "Error during parameters loading: " + e.getMessage(), e);
      }

      if (!TrainerFactory.isValid(params.getSettings())) {
        throw new TerminateToolException(1, "Training parameters file '" + paramFile + "' is invalid!");
      }

      TrainerFactory.TrainerType trainerType = TrainerFactory.getTrainerType(params.getSettings());

      if (!supportSequenceTraining
          && trainerType.equals(TrainerFactory.TrainerType.EVENT_MODEL_SEQUENCE_TRAINER)) {
        throw new TerminateToolException(1, "Sequence training is not supported!");
      }
    }

    return params;
  }
}
