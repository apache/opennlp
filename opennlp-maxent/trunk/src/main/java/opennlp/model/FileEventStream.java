/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

import opennlp.maxent.GIS;
import opennlp.maxent.io.SuffixSensitiveGISModelWriter;

/** 
 * Class for using a file of events as an event stream.  The format of the file is one event perline with
 * each line consisting of outcome followed by contexts (space delimited).
 * @author Tom Morton
 *
 */
public class FileEventStream extends  AbstractEventStream {

  BufferedReader reader;
  String line;
  
  /**
   * Creates a new file event stream from the specified file name.
   * @param fileName the name fo the file containing the events.
   * @throws IOException When the specified file can not be read.
   */
  public FileEventStream(String fileName, String encoding) throws IOException {
    if (encoding == null) {
      reader = new BufferedReader(new FileReader(fileName));
    }
    else {
      reader = new BufferedReader(new InputStreamReader(new FileInputStream(fileName),encoding));
    }
  }
  
  public FileEventStream(String fileName) throws IOException {
    this(fileName,null);
  }
    
  /**
   * Creates a new file event stream from the specified file.
   * @param file the file containing the events.
   * @throws IOException When the specified file can not be read.
   */
  public FileEventStream(File file) throws IOException {
    reader = new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF8"));
  }
  
  public boolean hasNext() {
    try {
      return (null != (line = reader.readLine()));
    }
    catch (IOException e) {
      System.err.println(e);
      return (false);
    }
  }
  
  public Event next() {
    StringTokenizer st = new StringTokenizer(line);
    String outcome = st.nextToken();
    int count = st.countTokens();
    String[] context = new String[count];
    for (int ci = 0; ci < count; ci++) {
      context[ci] = st.nextToken();
    }
    return (new Event(outcome, context));
  }
  
  /**
   * Generates a string representing the specified event.
   * @param event The event for which a string representation is needed.
   * @return A string representing the specified event.
   */
  public static String toLine(Event event) {
    StringBuffer sb = new StringBuffer();
    sb.append(event.getOutcome());
    String[] context = event.getContext();
    for (int ci=0,cl=context.length;ci<cl;ci++) {
      sb.append(" "+context[ci]);
    }
    sb.append(System.getProperty("line.separator"));
    return sb.toString();
  }
  
  /**
   * Trains and writes a model based on the events in the specified event file.
   * the name of the model created is based on the event file name.
   * @param args eventfile [iterations cuttoff]
   * @throws IOException when the eventfile can not be read or the model file can not be written.
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("Usage: FileEventStream eventfile [iterations cutoff]");
      System.exit(1);
    }
    int ai=0;
    String eventFile = args[ai++];
    EventStream es = new FileEventStream(eventFile);
    int iterations = 100;
    int cutoff = 5;
    if (ai < args.length) {
      iterations = Integer.parseInt(args[ai++]);
      cutoff = Integer.parseInt(args[ai++]);
    }
    AbstractModel model = GIS.trainModel(es,iterations,cutoff);
    new SuffixSensitiveGISModelWriter(model, new File(eventFile+".bin.gz")).persist();
  }
}

