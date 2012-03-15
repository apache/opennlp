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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import opennlp.tools.util.ObjectStream;

/**
 * The directory sample stream scans a directory (recursively) for plain text
 * files and outputs each file as a String object.
 */
public class DirectorySampleStream implements ObjectStream<String> {

  private final Charset encoding;
  
  private final List<File> inputDirectories;
  
  private final boolean isRecursiveScan;
  
  private final FileFilter fileFilter;
  
  private Stack<File> directories = new Stack<File>();
  
  private Stack<File> textFiles = new Stack<File>();
  
  public DirectorySampleStream(File dirs[], Charset encoding, FileFilter fileFilter, boolean recursive) {

    this.encoding = encoding;
    this.fileFilter= fileFilter; 
    isRecursiveScan = recursive;
    
    List<File> inputDirectoryList = new ArrayList<File>(dirs.length);
    
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
  
  public DirectorySampleStream(File dir, Charset encoding, FileFilter fileFilter, boolean recursive) {
    this(new File[]{dir}, encoding, fileFilter, recursive);
  }
  
  static String readFile(File textFile, Charset encoding) throws IOException {
    
    Reader in = new BufferedReader(new InputStreamReader(new FileInputStream(textFile), encoding));

    StringBuilder text = new StringBuilder();
    
    try {
      char buffer[] = new char[1024];
      int length;
      while ((length = in.read(buffer, 0, buffer.length)) > 0) {
        text.append(buffer, 0, length);
      }
    }
    finally {
      try {
        in.close();
      }
      catch (IOException e) {
        // sorry that this can fail!
      }
    }
    
    return text.toString();
  }
  
  public String read() throws IOException {

    while(textFiles.isEmpty() && !directories.isEmpty()) {
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
      return readFile(textFiles.pop(), encoding);
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
