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

package opennlp.tools.formats.masc;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import javax.xml.parsers.SAXParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.XmlUtil;

public class MascDocumentStream implements ObjectStream<MascDocument> {

  private static final Logger logger = LoggerFactory.getLogger(MascDocumentStream.class);
  /**
   * A helper class to parse the header (.hdr) files.
   */
  private static class HeaderHandler extends DefaultHandler {
    private HashMap<String, String> annotationFiles = null;
    private String file = null;
    private String fType = null;

    protected HashMap<String, String> getPathList() {
      return annotationFiles;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
        throws SAXException {

      // create a new annotation file and put it in map
      // initialize File object and set path attribute
      if (qName.equalsIgnoreCase("annotation") ||
          qName.equalsIgnoreCase("primaryData")) {
        file = attributes.getValue("loc");
        fType = attributes.getValue("f.id");

        // initialize list
        if (annotationFiles == null) {
          annotationFiles = new HashMap<>();
        }
      }

    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {

      // add annotation object to list
      if (qName.equalsIgnoreCase("annotation") ||
          qName.equalsIgnoreCase("primaryData")) {
        annotationFiles.put(fType, file);
      }

    }

  }
  private final List<MascDocument> documents = new LinkedList<>();
  private Iterator<MascDocument> documentIterator;
  private final SAXParser saxParser;

  public MascDocumentStream(File mascCorpusDirectory) throws IOException {
    this(mascCorpusDirectory, true, pathname -> pathname.getName().contains(""));
  }

  /**
   * Creates a MascDocumentStream to read the documents from a given directory.
   * Works iff all annotation files mentioned in the headers are present.
   *
   * @param mascCorpusDirectory the directory containing all the MASC files
   * @param searchRecursive     whether the search should go through subdirectories
   * @param fileFilter          a custom file filter to filter out some files or
   *                            null to accept anything
   * @throws IOException if any stage of the stream creation fails
   */
  public MascDocumentStream(File mascCorpusDirectory,
                            boolean searchRecursive, FileFilter fileFilter) throws IOException {

    saxParser = XmlUtil.createSaxParser();

    if (!mascCorpusDirectory.isDirectory()) {
      throw new IOException("Input corpus directory must be a directory " +
          "according to File.isDirectory()!");
    }

    int failedLoads = 0;
    Stack<File> directoryStack = new Stack<>();
    directoryStack.add(mascCorpusDirectory);

    while (!directoryStack.isEmpty()) {
      for (File file : directoryStack.pop().listFiles(fileFilter)) {
        if (file.isFile()) {
          String hdrFilePath = file.getAbsolutePath();

          // look for the header files
          if (hdrFilePath.endsWith(".hdr")) {

            HashMap<String, File> fileGroup = checkAnnotations(hdrFilePath);
            InputStream f_primary = new BufferedInputStream(
                new FileInputStream(fileGroup.get("f.text")));
            InputStream f_seg = (fileGroup.containsKey("f.seg")) ?
                new BufferedInputStream(new FileInputStream(fileGroup.get("f.seg"))) : null;
            InputStream f_penn = (fileGroup.containsKey("f.penn")) ?
                new BufferedInputStream(new FileInputStream(fileGroup.get("f.penn"))) : null;
            InputStream f_s = (fileGroup.containsKey("f.s")) ?
                new BufferedInputStream(new FileInputStream(fileGroup.get("f.s"))) : null;
            InputStream f_ne = (fileGroup.containsKey("f.ne")) ?
                new BufferedInputStream(new FileInputStream(fileGroup.get("f.ne"))) : null;

            try {
              documents.add(MascDocument.parseDocument(hdrFilePath, f_primary, f_seg,
                  f_penn, f_s, f_ne));
            } catch (IOException e) {
              logger.error("Failed to parse the file: {}", hdrFilePath, e);
              failedLoads++;
            }
          }

        } else if (searchRecursive && file.isDirectory()) {
          directoryStack.push(file);
        }
      }
    }

    logger.info("Documents loaded: {}", documents.size());
    if (failedLoads > 0) {
      logger.info("Failed loading {} documents.", failedLoads);
    }
    reset();

  }

  /**
   * Check that all annotation files mentioned in the header are present
   *
   * @param path The path to header
   * @throws IOException If corpus integrity is violated
   */
  private HashMap<String, File> checkAnnotations(String path) throws IOException {
    HeaderHandler handler = new HeaderHandler();
    HashMap<String, File> fileGroup = new HashMap<>();
    File hdrFile = new File(path);
    try {
      saxParser.parse(hdrFile, handler);
    } catch (SAXException e) {
      throw new IOException("Invalid corpus format. " +
          "Could not parse the header: " + path);
    }
    HashMap<String, String> annotationFiles = handler.getPathList();

    String pathToFolder = hdrFile.getParentFile().getAbsolutePath();
    for (Map.Entry<String, String> annotation : annotationFiles.entrySet()) {
      File file = new File(pathToFolder, annotation.getValue());
      if (!(file.isFile() && file.exists())) {
        throw new IOException("Corpus integrity violated. " +
            "Annotation file " + file.getAbsolutePath() + " is missing.");
      }

      fileGroup.put(annotation.getKey(), file);

    }

    return fileGroup;

  }

  /**
   * Reset the reading of all documents to the first sentence.
   * Reset the corpus to the first document.
   */
  @Override
  public void reset() {
    for (MascDocument doc : documents) {
      doc.reset();
    }
    documentIterator = documents.iterator();
  }

  /**
   * Return the next document. Client needs to check if this document has the necessary annotations.
   *
   * @return A corpus document with all its annotations.
   * @throws IOException if anything goes wrong.
   */
  @Override
  public MascDocument read() throws IOException {

    MascDocument doc = null;

    if (documentIterator.hasNext()) {
      doc = documentIterator.next();
    }

    return doc;
  }

  /**
   * Remove the corpus from the memory.
   */
  @Override
  public void close() {
    documentIterator = null;
  }

}

