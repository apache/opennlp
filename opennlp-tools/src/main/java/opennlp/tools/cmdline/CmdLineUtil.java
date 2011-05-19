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
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import opennlp.model.TrainUtil;
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
   * should start with a capital letter.
   * 
   * @param inFile the particular file to check to qualify an input file
   * 
   * @throws TerminateToolException  if test does not pass this exception is
   * thrown and an error message is printed to the console.
   */
  public static void checkInputFile(String name, File inFile) {
    
    boolean isFailure;
    
    if (inFile.isDirectory()) {
      System.err.println("The " + name + " file is a directory!");
      isFailure = true;
    }
    else if (!inFile.exists()) {
      System.err.println("The " + name + " file does not exist!");
      isFailure = true;
    }
    else if (!inFile.canRead()) {
      System.err.println("No permissions to read the " + name + " file!");
      isFailure = true;
    }
    else {
      isFailure = false;
    }
    
    if (isFailure) {
      System.err.println("Path: " + inFile.getAbsolutePath());
      throw new TerminateToolException(-1);
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
   * @param name
   * @param outFile
   */
  public static void checkOutputFile(String name, File outFile) {
    
    boolean isFailure = true;
    
    if (outFile.exists()) {
      
      // The file already exists, ensure that it is a normal file and that it is
      // possible to write into it
      
      if (outFile.isDirectory()) {
        System.err.println("The " + name + " file is a directory!");
      }
      else if (outFile.isFile()) {
        if (outFile.canWrite()) {
          isFailure = false;
        }
        else {
          System.err.println("No permissions to write the " + name + " file!");
        }
      }
      else {
        System.err.println("The " + name + " file is not a normal file!");
      }
    }
    else {
      
      // The file does not exist ensure its parent
      // directory exists and has write permissions to create
      // a new file in it
      
      File parentDir = outFile.getAbsoluteFile().getParentFile();
      
      if (parentDir != null && parentDir.exists()) {
        
        if (parentDir.canWrite()) {
          isFailure = false;
        }
        else {
          System.err.println("No permissions to create the " + name + " file!");
        }
      }
      else {
        System.err.println("The parent directory of the " + name + " file does not exist, " +
        		"please create it first!");
      }
      
    }
    
    if (isFailure) {
      System.err.println("Path: " + outFile.getAbsolutePath());
      throw new TerminateToolException(-1);
    }
  }
  
  public static FileInputStream openInFile(File file) {
    try {
      return new FileInputStream(file);
    } catch (FileNotFoundException e) {
      System.err.println("File cannot be found: " + e.getMessage());
      throw new TerminateToolException(-1);
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
    
    OutputStream modelOut = null;
    try {
      modelOut = new BufferedOutputStream(new FileOutputStream(modelFile), IO_BUFFER_SIZE);
      model.serialize(modelOut);
    } catch (IOException e) {
      System.err.println("failed");
      System.err.println("Error during writing model file: " + e.getMessage());
      throw new TerminateToolException(-1);
    } finally {
      if (modelOut != null) {
        try {
          modelOut.close();
        } catch (IOException e) {
          System.err.println("Failed to properly close model file: " + 
              e.getMessage());
        }
      }
    }
    
    long modelWritingDuration = System.currentTimeMillis() - beginModelWritingTime;
    
    System.err.printf("done (%.3fs)\n", modelWritingDuration / 1000d);
    
    System.err.println();
    
    System.err.println("Wrote " + modelName + " model to");
    System.err.println("path: " + modelFile.getAbsolutePath());
    
    System.err.println();
  }
  
  /**
   * Retrieves the specified parameters from the given arguments.
   * 
   * @param param
   * @param args
   * @return
   */
  public static String getParameter(String param, String args[]) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].startsWith("-") && args[i].equals(param)) {
        i++;
        if (i < args.length) {
          return args[i];
        }
      }
    }
    
    return null;
  }
  
  /**
   * Retrieves the specified parameters from the specified arguments.
   * 
   * @param param
   * @param args
   * @return
   */
  public static Integer getIntParameter(String param, String args[]) {
    String value = getParameter(param, args);
    
    try {
      if (value != null)
          return Integer.parseInt(value);
    }
    catch (NumberFormatException e) {
    }
    
    return null;
  }
  
  /**
   * Retrieves the specified parameters from the specified arguments.
   * 
   * @param param
   * @param args
   * @return
   */
  public static Double getDoubleParameter(String param, String args[]) {
    String value = getParameter(param, args);
    
    try {
      if (value != null)
          return Double.parseDouble(value);
    }
    catch (NumberFormatException e) {
    }
    
    return null;
  }
  
  /**
   * Retrieves the "-encoding" parameter.
   *
   * @param param
   * @param args
   * 
   * @return the encoding or if invalid the VM is killed.
   */
  public static Charset getEncodingParameter(String args[]) {
    String charsetName = getParameter("-encoding", args);

    try {
      if (charsetName != null) {
        if (Charset.isSupported(charsetName)) {
          return Charset.forName(charsetName);
        } else {
          System.out.println("Error: Unsuppoted encoding " + charsetName + ".");
          throw new TerminateToolException(-1);
        }
      }
    } catch (IllegalCharsetNameException e) {
      System.out.println("Error: encoding name(" + e.getCharsetName()
          + ") is invalid.");
      throw new TerminateToolException(-1);
    }
    
    // TODO: Can still return null if encoding is not specified at all ...
    return null;
  }
  
  public static void checkLanguageCode(String code) {
    List<String> languageCodes  = new ArrayList<String>();
    languageCodes.addAll(Arrays.asList(Locale.getISOLanguages()));
    languageCodes.add("x-unspecified");
    
    if (!languageCodes.contains(code)) {
      System.err.println("Unkown language code, must be an ISO 639 code!");
      throw new TerminateToolException(-1);
    }
  }
  
  public static boolean containsParam(String param, String args[]) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals(param)) {
        return true;
      }
    }
    
    return false;
  }
  
  public static void printTrainingIoError(IOException e) {
    System.err.println("IO error while reading training data or indexing data: " + e.getMessage());
  }
  
  public static void handleStdinIoError(IOException e) {
    System.err.println("IO Error while reading from stdin: " + e.getMessage());
    throw new TerminateToolException(-1);
  }
  
  // its optional, passing null is allowed
  public static TrainingParameters loadTrainingParameters(String paramFile,
      boolean supportSequenceTraining) {
    
    TrainingParameters params = null;
    
    if (paramFile != null) {
      
      checkInputFile("Training Parameter", new File(paramFile));
      
      InputStream paramsIn = null;
      try {
        paramsIn = new FileInputStream(new File(paramFile));
        
        params = new opennlp.tools.util.TrainingParameters(paramsIn);
      } catch (IOException e) {
        // TODO: print error and exit
        e.printStackTrace();
      }
      finally {
        try {
          if (paramsIn != null)
            paramsIn.close();
        } catch (IOException e) {
        }
      }
      
      if (!TrainUtil.isValid(params.getSettings())) {
        System.err.println("Training parameters file is invalid!");
        throw new TerminateToolException(-1);
      }
      
      if (!supportSequenceTraining && TrainUtil.isSequenceTraining(params.getSettings())) {
        System.err.println("Sequence training is not supported!");
        throw new TerminateToolException(-1);
      }
    }
    
    return params;
  }
}
