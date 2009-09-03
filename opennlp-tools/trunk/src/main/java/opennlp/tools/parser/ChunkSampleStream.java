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

package opennlp.tools.parser;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.parser.chunking.Parser;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamException;

public class ChunkSampleStream implements ObjectStream<ChunkSample> {

  private ObjectStream<Parse> in;
  
  public ChunkSampleStream(ObjectStream<Parse> in) {
    this.in = in;
  }
  
  public ChunkSample read() throws ObjectStreamException {
    
    Parse parse = in.read();
    
    if (parse != null) {
      List<String> toks = new ArrayList<String>();
      List<String> tags = new ArrayList<String>();
      List<String> preds = new ArrayList<String>();
      
      if (parse.isPosTag()) {
        toks.add(parse.toString());
        tags.add(parse.getType());
        preds.add(Parser.OTHER);
      }
      else {
        boolean start = true;
        String ctype = parse.getType();
        Parse[] kids = parse.getChildren();
        for (int ti=0,tl=kids.length;ti<tl;ti++) {
          Parse tok = kids[ti];
          toks.add(tok.toString());
          tags.add(tok.getType());
          if (start) {
            preds.add(Parser.START + ctype);
            start = false;
          }
          else {
            preds.add(Parser.CONT + ctype);
          }
        }
      }
      
      return new ChunkSample(toks.toArray(new String[toks.size()]), 
          tags.toArray(new String[tags.size()]), 
          preds.toArray(new String[preds.size()]));
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
