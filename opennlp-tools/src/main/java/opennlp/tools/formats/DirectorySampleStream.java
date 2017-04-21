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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import opennlp.tools.util.ObjectStream;

/**
 * The directory sample stream allows for creating a stream
 * from a directory listing of files.
 */
public class DirectorySampleStream implements ObjectStream<File> {

  private final List<File> inputDirectories;

  private final boolean recursive;

  private final FileFilter fileFilter;

  private Stack<File> directories = new Stack<>();

  private Stack<File> textFiles = new Stack<>();
  
  /**
   * Creates a new directory sample stream.
   * @param dirs The directories to read.
   * @param fileFilter The {@link FileFilter filter} to apply while enumerating files.
   * @param recursive Enables or disables recursive file listing.
   */
  public DirectorySampleStream(File[] dirs, FileFilter fileFilter, boolean recursive) {
    this.fileFilter = fileFilter;
    this.recursive = recursive;

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

  /**
   * Creates a new directory sample stream.
   * @param dir The {@link File directory}.
   * @param fileFilter The {@link FileFilter filter} to apply while enumerating files.
   * @param recursive Enables or disables recursive file listing.
   */
  public DirectorySampleStream(File dir, FileFilter fileFilter, boolean recursive) {
    this(new File[]{dir}, fileFilter, recursive);
  }

  @Override
  public File read() throws IOException {

    while (textFiles.isEmpty() && !directories.isEmpty()) {
      File dir = directories.pop();

      File[] files;

      if (fileFilter != null) {
        files = dir.listFiles(fileFilter);
      }
      else {
        files = dir.listFiles();
      }

      Arrays.sort(files);

      for (File file : files) {
        if (file.isFile()) {
          textFiles.push(file);
        }
        else if (recursive && file.isDirectory()) {
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

  @Override
  public void reset() {
    directories.clear();
    textFiles.clear();

    directories.addAll(inputDirectories);
  }

  /**
   * {@inheritDoc}
   * Calling this function has no effect on
   * the stream.
   */
  @Override
  public void close() throws IOException {
  }
}
