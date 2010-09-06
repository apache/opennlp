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

import java.io.File;
import java.io.IOException;

import opennlp.maxent.GIS;
import opennlp.maxent.io.SuffixSensitiveGISModelWriter;

public class RealValueFileEventStream extends FileEventStream {

  public RealValueFileEventStream(String fileName) throws IOException {
    super(fileName);
  }
  
  public RealValueFileEventStream(File file) throws IOException {
    super(file);
  }
  
  /**
   * Parses the specified contexts and re-populates context array with features and returns the values
   * for these features.
   * If all values are unspecified, then null is returned.
   * @param contexts The contexts with real values specified.
   * @return The value for each context or null if all values are unspecified.
   */
  public static float[] parseContexts(String[] contexts) {
    boolean hasRealValue = false;
    float[] values = new float[contexts.length]; 
    for (int ci = 0; ci < contexts.length; ci++) {
      int ei = contexts[ci].lastIndexOf("=");
      if (ei > 0 && ei+1 < contexts[ci].length()) {
        boolean gotReal = true;
        try {
          values[ci] = Float.parseFloat(contexts[ci].substring(ei+1));
        }
        catch (NumberFormatException e) {
          gotReal = false;
          System.err.println("Unable to determine value in context:"+contexts[ci]);
          values[ci] = 1;
        }
        if (gotReal) {
          if (values[ci] < 0) {
            throw new RuntimeException("Negitive values are not allowed: "+contexts[ci]);
          }
          contexts[ci] = contexts[ci].substring(0,ei);
          hasRealValue = true;
        }
      }
      else {
        values[ci] = 1;
      }
    }
    if (!hasRealValue) {
      values = null;
    }
    return values;
  }
    
  public Event next() {
    int si = line.indexOf(' ');
    String outcome = line.substring(0,si);
    String[] contexts = line.substring(si+1).split(" ");
    float[] values = parseContexts(contexts);
    return (new Event(outcome, contexts, values));
  }  
  
  /**
   * Trains and writes a model based on the events in the specified event file.
   * the name of the model created is based on the event file name.
   * @param args eventfile [iterations cuttoff]
   * @throws IOException when the eventfile can not be read or the model file can not be written.
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("Usage: RealValueFileEventStream eventfile [iterations cutoff]");
      System.exit(1);
    }
    int ai=0;
    String eventFile = args[ai++];
    EventStream es = new RealValueFileEventStream(eventFile);
    int iterations = 100;
    int cutoff = 5;
    if (ai < args.length) {
      iterations = Integer.parseInt(args[ai++]);
      cutoff = Integer.parseInt(args[ai++]);
    }
    AbstractModel model = GIS.trainModel(iterations,new OnePassRealValueDataIndexer(es,cutoff));
    new SuffixSensitiveGISModelWriter(model, new File(eventFile+".bin.gz")).persist();
  }
}
