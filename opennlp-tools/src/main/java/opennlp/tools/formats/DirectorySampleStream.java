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
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import opennlp.tools.util.ObjectStream;

/**
 * The directory sample stream scans a directory (recursively) for plain text
 * files and outputs each file as a String object.
 */
public class DirectorySampleStream implements ObjectStream<File> {

  private final List<File> inputDirectories;

  private final boolean isRecursiveScan;

  private final FileFilter fileFilter;

  private Stack<File> directories = new Stack<>();

  private Stack<File> textFiles = new Stack<>();

  public DirectorySampleStream(File dirs[], FileFilter fileFilter, boolean recursive) {
    this.fileFilter = fileFilter;
    isRecursiveScan = recursive;

    List<File> inputDirectoryList = new ArrayList<>(dirs.length);

    for (File dir : dirs) {
      if (!dir.isDirectory()) {
        throw new IllegalArgumentException(
            "All passed in directories must be directories, but \""
            + dir.toString() + "\" is not!");
      }

      inputDirectoryList.add(dir);
    }

    inputDirectories = Collections.unmodifiableList(inputDirectoryList);

    directories.addAll(inputDirectories);
  }

  public DirectorySampleStream(File dir, FileFilter fileFilter, boolean recursive) {
    this(new File[]{dir}, fileFilter, recursive);
  }

  public File read() throws IOException {

    while (textFiles.isEmpty() && !directories.isEmpty()) {
      File dir = directories.pop();

      File files[];

      if (fileFilter != null) {
        files = dir.listFiles(fileFilter);
      }
      else {
        files = dir.listFiles();
      }

      for (File file : files) {
        if (file.isFile()) {
          textFiles.push(file);
        }
        else if (isRecursiveScan && file.isDirectory()) {
          directories.push(file);
        }
      }
    }

    if (!textFiles.isEmpty()) {
      return textFiles.pop();
    }
    else {
      return null;
    }
  }

  public void reset() {
    directories.clear();
    textFiles.clear();

    directories.addAll(inputDirectories);
  }

  public void close() throws IOException {
  }
}
