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

package opennlp.tools.formats.muc;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.formats.muc.MucCorefContentHandler.CorefMention;
import opennlp.tools.parser.Parse;

/**
 * A coreference sample as it is extracted from MUC style training data.
 */
public class RawCorefSample {
  
  private List<String[]> texts = new ArrayList<String[]>();
  private List<CorefMention[]> mentions = new ArrayList<CorefMention[]>();
  
  private List<Parse> parses;
  
  RawCorefSample(List<String> texts, List<CorefMention[]> mentions) {
  }
  
  public List<String[]> getTexts() {
    return texts;
  }
  
  public List<CorefMention[]> getMentions() {
    return mentions;
  }
  
  void setParses(List<Parse> parses) {
    this.parses = parses;
  }
  
  List<Parse> getParses() {
    return parses;
  }
}
