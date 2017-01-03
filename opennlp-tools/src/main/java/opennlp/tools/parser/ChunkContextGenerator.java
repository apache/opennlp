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

import opennlp.tools.chunker.ChunkerContextGenerator;
import opennlp.tools.util.Cache;

/**
 * Creates predivtive context for the pre-chunking phases of parsing.
 */
public class ChunkContextGenerator implements ChunkerContextGenerator {

  private static final String EOS = "eos";
  private Cache<String, String[]> contextsCache;
  private Object wordsKey;


  public ChunkContextGenerator() {
    this(0);
  }

  public ChunkContextGenerator(int cacheSize) {
    super();
    if (cacheSize > 0) {
      contextsCache = new Cache<>(cacheSize);
    }
  }

  public String[] getContext(Object o) {
    Object[] data = (Object[]) o;
    return getContext((Integer) data[0], (String[]) data[1], (String[]) data[2], (String[]) data[3]);
  }

  public String[] getContext(int i, String[] words, String[] prevDecisions, Object[] ac) {
    return getContext(i,words,(String[]) ac[0],prevDecisions);
  }

  public String[] getContext(int i, String[] words, String[] tags, String[] preds) {
    List<String> features = new ArrayList<>(19);
    int x_2 = i - 2;
    int x_1 = i - 1;
    int x2 = i + 2;
    int x1 = i + 1;

    String w_2,w_1,w0,w1,w2;
    String t_2,t_1,t0,t1,t2;
    String p_2,p_1;

    // chunkandpostag(-2)
    if (x_2 >= 0) {
      t_2 = tags[x_2];
      p_2 = preds[x_2];
      w_2 = words[x_2];
    }
    else {
      t_2 = EOS;
      p_2 = EOS;
      w_2 = EOS;
    }

    // chunkandpostag(-1)
    if (x_1 >= 0) {
      t_1 = tags[x_1];
      p_1 = preds[x_1];
      w_1 = words[x_1];
    }
    else {
      t_1 = EOS;
      p_1 = EOS;
      w_1 = EOS;
    }

    // chunkandpostag(0)
    t0 = tags[i];
    w0 = words[i];

    // chunkandpostag(1)
    if (x1 < tags.length) {
      t1 = tags[x1];
      w1 = words[x1];
    }
    else {
      t1 = EOS;
      w1 = EOS;
    }

    // chunkandpostag(2)
    if (x2 < tags.length) {
      t2 = tags[x2];
      w2 = words[x2];
    }
    else {
      t2 = EOS;
      w2 = EOS;
    }

    String cacheKey = i + t_2 + t1 + t0 + t1 + t2 + p_2 + p_1;
    if (contextsCache != null) {
      if (wordsKey == words) {
        String[] contexts = contextsCache.get(cacheKey);
        if (contexts != null) {
          return contexts;
        }
      }
      else {
        contextsCache.clear();
        wordsKey = words;
      }
    }

    String ct_2 = chunkandpostag(-2, w_2, t_2, p_2);
    String ctbo_2 = chunkandpostagbo(-2, t_2, p_2);
    String ct_1 = chunkandpostag(-1, w_1, t_1, p_1);
    String ctbo_1 = chunkandpostagbo(-1, t_1, p_1);
    String ct0 = chunkandpostag(0, w0, t0, null);
    String ctbo0 = chunkandpostagbo(0, t0, null);
    String ct1 = chunkandpostag(1, w1, t1, null);
    String ctbo1 = chunkandpostagbo(1, t1, null);
    String ct2 = chunkandpostag(2, w2, t2, null);
    String ctbo2 = chunkandpostagbo(2, t2, null);

    features.add("default");
    features.add(ct_2);
    features.add(ctbo_2);
    features.add(ct_1);
    features.add(ctbo_1);
    features.add(ct0);
    features.add(ctbo0);
    features.add(ct1);
    features.add(ctbo1);
    features.add(ct2);
    features.add(ctbo2);

    //chunkandpostag(-1,0)
    features.add(ct_1 + "," + ct0);
    features.add(ctbo_1 + "," + ct0);
    features.add(ct_1 + "," + ctbo0);
    features.add(ctbo_1 + "," + ctbo0);

    //chunkandpostag(0,1)
    features.add(ct0 + "," + ct1);
    features.add(ctbo0 + "," + ct1);
    features.add(ct0 + "," + ctbo1);
    features.add(ctbo0 + "," + ctbo1);
    String contexts[] = features.toArray(new String[features.size()]);
    if (contextsCache != null) {
      contextsCache.put(cacheKey,contexts);
    }
    return contexts;
  }

  private String chunkandpostag(int i, String tok, String tag, String chunk) {
    StringBuilder feat = new StringBuilder(20);
    feat.append(i).append("=").append(tok).append("|").append(tag);
    if (i < 0) {
      feat.append("|").append(chunk);
    }
    return feat.toString();
  }

  private String chunkandpostagbo(int i, String tag, String chunk) {
    StringBuilder feat = new StringBuilder(20);
    feat.append(i).append("*=").append(tag);
    if (i < 0) {
      feat.append("|").append(chunk);
    }
    return feat.toString();
  }
}
