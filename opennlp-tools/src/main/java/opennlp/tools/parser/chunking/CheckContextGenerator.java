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

package opennlp.tools.parser.chunking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import opennlp.tools.parser.AbstractContextGenerator;
import opennlp.tools.parser.Parse;

/**
 * Class for generating predictive context for deciding when a constituent is complete.
 */
public class CheckContextGenerator extends AbstractContextGenerator {

  /**
   * Creates a new context generator for generating predictive context for deciding
   * when a constituent is complete.
   */
  public CheckContextGenerator() {
    super();
  }

  public String[] getContext(Object o) {
    Object[] params = (Object[]) o;
    return getContext((Parse[]) params[0], (String) params[1], (Integer) params[2], (Integer) params[3]);
  }

  /**
   * Returns predictive context for deciding whether the specified constituents between the
   * specified start and end index can be combined to form a new constituent of the specified type.
   *
   * @param constituents The constituents which have yet to be combined into new constituents.
   * @param type The type of the new constituent proposed.
   * @param start The first constituent of the proposed constituent.
   * @param end The last constituent of the proposed constituent.
   * @return The predictive context for deciding whether a new constituent should be created.
   */
  public String[] getContext(Parse[] constituents, String type, int start, int end) {
    int ps = constituents.length;
    List<String> features = new ArrayList<>(100);

    //default
    features.add("default");
    //first constituent label
    features.add("fl=" + constituents[0].getLabel());
    Parse pstart = constituents[start];
    Parse pend = constituents[end];
    checkcons(pstart, "begin", type, features);
    checkcons(pend, "last", type, features);
    StringBuilder production = new StringBuilder(20);
    StringBuilder punctProduction = new StringBuilder(20);
    production.append("p=").append(type).append("->");
    punctProduction.append("pp=").append(type).append("->");
    for (int pi = start; pi < end; pi++) {
      Parse p = constituents[pi];
      checkcons(p, pend, type, features);
      production.append(p.getType()).append(",");
      punctProduction.append(p.getType()).append(",");
      Collection<Parse> nextPunct = p.getNextPunctuationSet();
      if (nextPunct != null) {
        for (Iterator<Parse> pit = nextPunct.iterator(); pit.hasNext();) {
          Parse punct = pit.next();
          punctProduction.append(punct.getType()).append(",");
        }
      }
    }
    production.append(pend.getType());
    punctProduction.append(pend.getType());
    features.add(production.toString());
    features.add(punctProduction.toString());
    Parse p_2 = null;
    Parse p_1 = null;
    Parse p1 = null;
    Parse p2 = null;
    Collection<Parse> p1s = constituents[end].getNextPunctuationSet();
    Collection<Parse> p2s = null;
    Collection<Parse> p_1s = constituents[start].getPreviousPunctuationSet();
    Collection<Parse> p_2s = null;
    if (start - 2 >= 0) {
      p_2 = constituents[start - 2];
    }
    if (start - 1 >= 0) {
      p_1 = constituents[start - 1];
      p_2s = p_1.getPreviousPunctuationSet();
    }
    if (end + 1 < ps) {
      p1 = constituents[end + 1];
      p2s = p1.getNextPunctuationSet();
    }
    if (end + 2 < ps) {
      p2 = constituents[end + 2];
    }
    surround(p_1, -1, type, p_1s, features);
    surround(p_2, -2, type, p_2s, features);
    surround(p1, 1, type, p1s, features);
    surround(p2, 2, type, p2s, features);

    return features.toArray(new String[features.size()]);
  }
}
