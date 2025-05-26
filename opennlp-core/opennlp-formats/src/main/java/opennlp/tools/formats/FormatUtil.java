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

package opennlp.tools.formats;

import java.io.File;
import java.io.FileNotFoundException;

import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.commons.Internal;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.MarkableFileInputStreamFactory;

/**
 * Utility class for the OpenNLP formats package.
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 */
@Internal
public class FormatUtil {

  public static InputStreamFactory createInputStreamFactory(File file) {
    try {
      return new MarkableFileInputStreamFactory(file);
    } catch (FileNotFoundException e) {
      throw new TerminateToolException(-1, "File '" + file + "' cannot be found", e);
    }
  }

  /**
   * Check that the given input file is valid.
   * <p>
   * To pass the test it must:<br>
   * - exist<br>
   * - not be a directory,<br>
   * - and be accessibly.<br>
   *
   * @param name the name which is used to refer to the file in an error message, it
   *     should start with a capital letter.
   *
   * @param inFile the particular {@link File} to check to qualify an input file
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
}
