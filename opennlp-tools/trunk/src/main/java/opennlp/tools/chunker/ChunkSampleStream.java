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

package opennlp.tools.chunker;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamException;

public class ChunkSampleStream implements ObjectStream<ChunkSample> {

  private final ObjectStream<String> in;
  
  public ChunkSampleStream(ObjectStream<String> in) {
    
    if (in == null)
        throw new IllegalArgumentException("in must not be null!");
    
    this.in = in;
  }
  
  public ChunkSample read() throws ObjectStreamException {
    
    List<String> toks = new ArrayList<String>();
    List<String> tags = new ArrayList<String>();
    List<String> preds = new ArrayList<String>();
    
    for (String line = in.read(); line !=null && !line.equals(""); line = in.read()) {
      String[] parts = line.split(" ");
      if (parts.length != 3) {
        System.err.println("Skipping corrupt line: "+line);
      }
      else {
        toks.add(parts[0]);
        tags.add(parts[1]);
        preds.add(parts[2]);
      }
    }
    
    if (toks.size() > 0) {
      return new ChunkSample(toks.toArray(new String[toks.size()]), 
          tags.toArray(new String[tags.size()]), preds.toArray(new String[preds.size()]));
    }
    else {
      return null;
    }
  }

  public void reset() throws ObjectStreamException,
      UnsupportedOperationException {
    in.reset();
  }
  
  public void close() throws ObjectStreamException {
    in.close();
  }
}
