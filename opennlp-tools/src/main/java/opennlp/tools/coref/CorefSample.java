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

package opennlp.tools.coref;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.coref.mention.DefaultParse;
import opennlp.tools.parser.Parse;

public class CorefSample {

  private List<Parse> parses;

  public CorefSample(List<Parse> parses) {
    this.parses = parses;
  }
  
  public List<opennlp.tools.coref.mention.Parse> getParses() {
    
    List<opennlp.tools.coref.mention.Parse> corefParses =
        new ArrayList<opennlp.tools.coref.mention.Parse>();
    
    int sentNumber = 0;
    for (Parse parse : parses) {
      corefParses.add(new DefaultParse(parse, sentNumber++));
    }
    
    return corefParses;
  }
  
  @Override
  public String toString() {
    
    StringBuffer sb = new StringBuffer();
    
    for (Parse parse : parses) {
      parse.show(sb);
      sb.append('\n');
    }
    
    sb.append('\n');
    
    return sb.toString();
  }
  
  public static CorefSample parse(String corefSampleString) {
    
    List<Parse> parses = new ArrayList<Parse>();
    
    for (String line : corefSampleString.split("\\r?\\n")) {
      parses.add(Parse.parseParse(line));
    }
    
    return new CorefSample(parses);
  }
}
