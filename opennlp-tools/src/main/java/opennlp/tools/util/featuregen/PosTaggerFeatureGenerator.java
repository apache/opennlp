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

package opennlp.tools.util.featuregen;

import java.util.List;

public class PosTaggerFeatureGenerator implements AdaptiveFeatureGenerator {

  private final String SB = "S=begin";

  @Override
  public void createFeatures(List<String> features, String[] tokens, int index,
                             String[] tags) {

    String prev, prevprev = null;
    String tagprev, tagprevprev;
    tagprev = tagprevprev = null;

    if (index - 1 >= 0) {
      prev =  tokens[index - 1];
      tagprev =  tags[index - 1];

      if (index - 2 >= 0) {
        prevprev = tokens[index - 2];
        tagprevprev = tags[index - 2];
      }
      else {
        prevprev = SB;
      }
    }
    else {
      prev = SB;
    }

    // add the words and pos's of the surrounding context
    if (prev != null) {
      if (tagprev != null) {
        features.add("t=" + tagprev);
      }
      if (prevprev != null) {
        if (tagprevprev != null) {
          features.add("t2=" + tagprevprev + "," + tagprev);
        }
      }
    }
  }
}
