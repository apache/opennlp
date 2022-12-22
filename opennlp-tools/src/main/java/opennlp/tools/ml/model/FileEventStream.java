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
import java.nio.charset.StandardCharsets;
import java.util.StringTokenizer;

import opennlp.tools.util.ObjectStream;

/**
 * Class for using a file of {@link Event events} as an {@link ObjectStream event stream}.
 * The format of the file is one event per line with
 * each line consisting of outcome followed by contexts (space delimited).
 *
 * @see Event
 * @see ObjectStream
 */
public class FileEventStream implements ObjectStream<Event> {

  protected final BufferedReader reader;

  /**
   * Instantiates a {@link FileEventStream} from the specified file name.
   * 
   * @param fileName The name fo the file containing the events.
   * @param encoding The name of the {@link java.nio.charset.Charset character encoding}.
   *
   * @throws IOException Thrown if the specified file can not be read.
   */
  public FileEventStream(String fileName, String encoding) throws IOException {
    this(encoding == null ?
      new FileReader(fileName) : new InputStreamReader(new FileInputStream(fileName), encoding));
  }

  /**
   * Instantiates a {@link FileEventStream} from the specified file name.
   *
   * @param fileName The name fo the file containing the events.
   *
   * @throws IOException Thrown if the specified file can not be read.
   */
  public FileEventStream(String fileName) throws IOException {
    this(fileName,null);
  }

  /**
   * Instantiates a {@link FileEventStream} via a {@link Reader}.
   *
   * @param reader The {@link Reader} that holds events.
   *
   * @throws IOException Thrown if the specified file can not be read.
   */
  public FileEventStream(Reader reader) throws IOException {
    this.reader = new BufferedReader(reader);
  }

  /**
   * Instantiates a {@link FileEventStream} via a {@link File}.
   *
   * @param file The {@link File} that holds events.
   *
   * @throws IOException Thrown if the specified file can not be read.
   */
  public FileEventStream(File file) throws IOException {
    reader = new BufferedReader(new InputStreamReader(
             new FileInputStream(file), StandardCharsets.UTF_8));
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

  @Override
  public void close() throws IOException {
    reader.close();
  }

  /**
   * Generates a string representing the specified event.
   *
   * @param event The {@link Event} for which a string representation is needed.
   * @return A string representing the specified event.
   */
  public static String toLine(Event event) {
    StringBuilder sb = new StringBuilder();
    sb.append(event.getOutcome());
    String[] context = event.getContext();
    for (String s : context) {
      sb.append(" ").append(s);
    }
    sb.append(System.getProperty("line.separator"));
    return sb.toString();
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    throw new UnsupportedOperationException();
  }
}

