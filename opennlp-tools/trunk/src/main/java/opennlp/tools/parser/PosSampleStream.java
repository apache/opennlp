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

import opennlp.tools.postag.POSSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamException;

public class PosSampleStream implements ObjectStream<POSSample> {

  private ObjectStream<Parse> in;
  
  public PosSampleStream(ObjectStream<Parse> in) {
    if (in == null)
        throw new IllegalArgumentException("in must not be null!");
    
    this.in = in;
  }

  public POSSample read() throws ObjectStreamException {
    
    Parse parse = in.read();
    
    if (parse != null) {
      List<String> toks = new ArrayList<String>();
      List<String> preds = new ArrayList<String>();
      
      if (parse.isPosTag()) {
        toks.add(parse.toString());
        preds.add(parse.getType());
      }
      else {
        Parse[] kids = parse.getChildren();
        for (int ti=0,tl=kids.length;ti<tl;ti++) {
          Parse tok = kids[ti];
          toks.add(tok.toString());
          preds.add(tok.getType());
        }
      }
      
      return new POSSample(toks.toArray(new String[toks.size()]),
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
}
