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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import opennlp.tools.util.model.BaseModel;

public class CmdLineUtil {

  /**
   * Check that the given input file is valid.
   * <p>
   * To pass the test it must:<br>
   * - exist<br>
   * - not be a directory<br>
   * - accessibly<br>
   * <p>
   * If the test does not pass an error message is printed
   * and the VM is killed with <code>System.exit(-1)</code>.
   * 
   * @param name the name which is used to refer to the file in an error message, it
   * should start with a capital letter.
   * 
   * @param inFile the particular file to check to qualify an input file
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
      System.exit(-1);
    }
  }
  
  private static void checkOutputFile(String name, File outFile) {
    
    boolean isFailure;
    
    if (outFile.isDirectory()) {
      System.err.println("The " + name + " file is a directory!");
      isFailure = true;
    }
    else if (!outFile.canWrite()) {
      System.err.println("No permissions to write the " + name + " file!");
      isFailure = true;
    }
    else {
      isFailure = false;
    }
    
    if (isFailure) {
      System.err.println("Path: " + outFile.getAbsolutePath());
      System.exit(-1);
    }
  }
  
  public static FileInputStream openInFile(File file) {
    try {
      return new FileInputStream(file);
    } catch (FileNotFoundException e) {
      System.err.println("File cannot be found: " + e.getMessage());
      System.exit(-1);
      return null;
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

    OutputStream modelOut = null;
    try {
      modelOut = new FileOutputStream(modelFile);
      model.serialize(modelOut);
    } catch (IOException e) {
      System.err.println("Error during writing model file: " + e.getMessage());
      System.exit(-1);
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
    
    System.out.println("Wrote " + modelName + " model.");
    System.out.println("Path: " + modelFile.getAbsolutePath());
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
      return Integer.parseInt(value);
    }
    catch (NumberFormatException e) {
      return null;
    }
  }
  
  /**
   * Retrieves the "-encoding" parameter.
   *
   * @param param
   * @param args
   * 
   * @return the encoding or if invalid the VM is killed.
   */
  public static String getEncodingParameter(String args[]) {
    String value = getParameter("-encoding", args);
    
    // TODO:
    // check if encoding is valid
    // what to do if not ???
    // print error message ?
    
    return value;
  }
  
  public static boolean containsParam(String param, String args[]) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals(param)) {
        return true;
      }
    }
    
    return false;
  }
}
