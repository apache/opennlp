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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChunkSample {
  private final List<String> sentence;

  private final List<String> tags;
  
  private final List<String> preds;
  
  public ChunkSample(String[] sentence, String[] tags, String[] preds) {
    this.sentence = Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(sentence)));
    this.tags = Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(tags)));
    this.preds = Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(preds)));
  }
  
  public String[] getSentence() {
    return sentence.toArray(new String[sentence.size()]);
  }
  
  public String[] getTags() {
    return tags.toArray(new String[tags.size()]);
  }
  
  public String[] getPreds() {
    return preds.toArray(new String[preds.size()]);
  }
}
