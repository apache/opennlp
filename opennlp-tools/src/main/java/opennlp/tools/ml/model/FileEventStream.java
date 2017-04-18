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

package opennlp.tools.ml.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.StringTokenizer;

import opennlp.tools.util.ObjectStream;

/**
 * Class for using a file of events as an event stream.  The format of the file is one event perline with
 * each line consisting of outcome followed by contexts (space delimited).
 */
public class FileEventStream implements ObjectStream<Event> {

  protected final BufferedReader reader;

  /**
   * Creates a new file event stream from the specified file name.
   * @param fileName the name fo the file containing the events.
   * @throws IOException When the specified file can not be read.
   */
  public FileEventStream(String fileName, String encoding) throws IOException {
    this(encoding == null ?
      new FileReader(fileName) : new InputStreamReader(new FileInputStream(fileName), encoding));
  }

  public FileEventStream(String fileName) throws IOException {
    this(fileName,null);
  }

  public FileEventStream(Reader reader) throws IOException {
    this.reader = new BufferedReader(reader);
  }

  /**
   * Creates a new file event stream from the specified file.
   * @param file the file containing the events.
   * @throws IOException When the specified file can not be read.
   */
  public FileEventStream(File file) throws IOException {
    reader = new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF8"));
  }

  @Override
  public Event read() throws IOException {
    String line;
    if ((line = reader.readLine()) != null) {
      StringTokenizer st = new StringTokenizer(line);
      String outcome = st.nextToken();
      int count = st.countTokens();
      String[] context = new String[count];
      for (int ci = 0; ci < count; ci++) {
        context[ci] = st.nextToken();
      }

      return new Event(outcome, context);
    }
    else {
      return null;
    }
  }

  public void close() throws IOException {
    reader.close();
  }

  /**
   * Generates a string representing the specified event.
   * @param event The event for which a string representation is needed.
   * @return A string representing the specified event.
   */
  public static String toLine(Event event) {
    StringBuilder sb = new StringBuilder();
    sb.append(event.getOutcome());
    String[] context = event.getContext();
    for (int ci = 0,cl = context.length; ci < cl; ci++) {
      sb.append(" ").append(context[ci]);
    }
    sb.append(System.getProperty("line.separator"));
    return sb.toString();
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    throw new UnsupportedOperationException();
  }
}

