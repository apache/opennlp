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

/** Features based on chunking model described in Fei Sha and Fernando Pereira. Shallow
 *  parsing with conditional random fields. In Proceedings of HLT-NAACL 2003. Association
 *  for Computational Linguistics, 2003.
 */
public class DefaultChunkerContextGenerator implements ChunkerContextGenerator {

  /**
   * Creates the default context generator a chunker.
   */
  public DefaultChunkerContextGenerator() {
  }

  public String[] getContext(int index, String[] sequence,
      String[] priorDecisions, Object[] additionalContext) {
    return getContext(index,sequence,(String[]) additionalContext[0],priorDecisions);
  }

  public String[] getContext(int i, String[] toks, String[] tags, String[] preds) {
    // Words in a 5-word window
    String w_2, w_1, w0, w1, w2;

    // Tags in a 5-word window
    String t_2, t_1, t0, t1, t2;

    // Previous predictions
    String p_2, p_1;

    if (i < 2) {
      w_2 = "w_2=bos";
      t_2 = "t_2=bos";
      p_2 = "p_2=bos";
    }
    else {
      w_2 = "w_2=" + toks[i - 2];
      t_2 = "t_2=" + tags[i - 2];
      p_2 = "p_2" + preds[i - 2];
    }

    if (i < 1) {
      w_1 = "w_1=bos";
      t_1 = "t_1=bos";
      p_1 = "p_1=bos";
    }
    else {
      w_1 = "w_1=" + toks[i - 1];
      t_1 = "t_1=" + tags[i - 1];
      p_1 = "p_1=" + preds[i - 1];
    }

    w0 = "w0=" + toks[i];
    t0 = "t0=" + tags[i];

    if (i + 1 >= toks.length) {
      w1 = "w1=eos";
      t1 = "t1=eos";
    }
    else {
      w1 = "w1=" + toks[i + 1];
      t1 = "t1=" + tags[i + 1];
    }

    if (i + 2 >= toks.length) {
      w2 = "w2=eos";
      t2 = "t2=eos";
    }
    else {
      w2 = "w2=" + toks[i + 2];
      t2 = "t2=" + tags[i + 2];
    }

    String[] features = new String[] {
        //add word features
        w_2,
        w_1,
        w0,
        w1,
        w2,
        w_1 + w0,
        w0 + w1,

        //add tag features
        t_2,
        t_1,
        t0,
        t1,
        t2,
        t_2 + t_1,
        t_1 + t0,
        t0 + t1,
        t1 + t2,
        t_2 + t_1 + t0,
        t_1 + t0 + t1,
        t0 + t1 + t2,

        //add pred tags
        p_2,
        p_1,
        p_2 + p_1,

        //add pred and tag
        p_1 + t_2,
        p_1 + t_1,
        p_1 + t0,
        p_1 + t1,
        p_1 + t2,
        p_1 + t_2 + t_1,
        p_1 + t_1 + t0,
        p_1 + t0 + t1,
        p_1 + t1 + t2,
        p_1 + t_2 + t_1 + t0,
        p_1 + t_1 + t0 + t1,
        p_1 + t0 + t1 + t2,

        //add pred and word
        p_1 + w_2,
        p_1 + w_1,
        p_1 + w0,
        p_1 + w1,
        p_1 + w2,
        p_1 + w_1 + w0,
        p_1 + w0 + w1
    };

    return features;
  }
}
