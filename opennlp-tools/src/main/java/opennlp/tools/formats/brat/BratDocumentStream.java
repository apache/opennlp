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

package opennlp.tools.formats.brat;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import opennlp.tools.util.ObjectStream;

public class BratDocumentStream implements ObjectStream<BratDocument> {

  private AnnotationConfiguration config;
  private List<String> documentIds = new LinkedList<>();
  private Iterator<String> documentIdIterator;

  /**
   * Creates a BratDocumentStream which reads the documents from the given input directory.
   *
   * @param config the annotation.conf from the brat project as an Annotation Configuration object
   * @param bratCorpusDirectory the directory containing all the brat training data files
   * @param searchRecursive specifies if the corpus directory should be traversed recursively
   *     to find training data files.
   * @param fileFilter  a custom file filter to filter out certain files or null to accept all files
   *
   * @throws IOException if reading from the brat directory fails in anyway
   */
  public BratDocumentStream(AnnotationConfiguration config, File bratCorpusDirectory,
      boolean searchRecursive, FileFilter fileFilter) throws IOException {

    if (!bratCorpusDirectory.isDirectory()) {
      throw new IOException("Input corpus directory must be a directory " +
          "according to File.isDirectory()!");
    }

    this.config = config;

    Stack<File> directoryStack = new Stack<>();
    directoryStack.add(bratCorpusDirectory);

    while (!directoryStack.isEmpty()) {
      for (File file : directoryStack.pop().listFiles(fileFilter)) {

        if (file.isFile()) {
          String annFilePath = file.getAbsolutePath();
          if (annFilePath.endsWith(".ann")) {

            // cutoff last 4 chars ...
            String documentId = annFilePath.substring(0, annFilePath.length() - 4);

            File txtFile = new File(documentId + ".txt");

            if (txtFile.exists() && txtFile.isFile()) {
              documentIds.add(documentId);
            }
          }
        }
        else if (searchRecursive && file.isDirectory()) {
          directoryStack.push(file);
        }
      }
    }

    reset();
  }

  public BratDocument read() throws IOException {

    BratDocument doc = null;

    if (documentIdIterator.hasNext()) {
      String id = documentIdIterator.next();

      try (InputStream txtIn = new BufferedInputStream(new FileInputStream(id + ".txt"));
          InputStream annIn = new BufferedInputStream(new FileInputStream(id + ".ann"))) {
        doc = BratDocument.parseDocument(config, id, txtIn, annIn);
      }
    }

    return doc;
  }

  public void reset() {
    documentIdIterator = documentIds.iterator();
  }

  public void close() {
    // No longer needed, make the object unusable
    documentIds = null;
    documentIdIterator = null;
  }
}
