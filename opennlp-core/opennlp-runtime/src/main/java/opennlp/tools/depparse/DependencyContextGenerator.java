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

package opennlp.tools.depparse;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates the classification features for one arc-standard configuration: words and tags
 * of the topmost stack and frontmost buffer positions, their pairings, and a bucketed
 * distance between stack top and buffer front.
 *
 * <p>Instances hold no state and are safe to share between threads.</p>
 *
 * @since 3.0.0
 */
public class DependencyContextGenerator {

  private static final String ROOT_VALUE = "*ROOT*";
  private static final String NONE_VALUE = "*NULL*";

  /**
   * Generates the features of the current configuration.
   *
   * @param state The configuration to describe. Must not be {@code null}.
   * @param tokens The sentence tokens. Must not be {@code null}.
   * @param tags The part-of-speech tags aligned with {@code tokens}. Must not be
   *             {@code null}.
   * @return The feature strings. Never {@code null}.
   * @throws IllegalArgumentException Thrown if any parameter is {@code null}.
   */
  public String[] getContext(ArcStandardState state, String[] tokens, String[] tags) {
    if (state == null || tokens == null || tags == null) {
      throw new IllegalArgumentException("state, tokens and tags must not be null");
    }
    final int s0 = state.stack(0);
    final int s1 = state.stack(1);
    final int s2 = state.stack(2);
    final int b0 = state.buffer(0);
    final int b1 = state.buffer(1);
    final int b2 = state.buffer(2);

    final String s0w = word(tokens, s0);
    final String s0t = tag(tags, s0);
    final String s1w = word(tokens, s1);
    final String s1t = tag(tags, s1);
    final String s2t = tag(tags, s2);
    final String b0w = word(tokens, b0);
    final String b0t = tag(tags, b0);
    final String b1t = tag(tags, b1);
    final String b2t = tag(tags, b2);

    final List<String> features = new ArrayList<>(20);
    features.add("s0w=" + s0w);
    features.add("s0t=" + s0t);
    features.add("s1w=" + s1w);
    features.add("s1t=" + s1t);
    features.add("s2t=" + s2t);
    features.add("b0w=" + b0w);
    features.add("b0t=" + b0t);
    features.add("b1t=" + b1t);
    features.add("b2t=" + b2t);
    features.add("s0wt=" + s0w + '/' + s0t);
    features.add("s0w,b0w=" + s0w + '|' + b0w);
    features.add("s0t,b0t=" + s0t + '|' + b0t);
    features.add("s1t,s0t=" + s1t + '|' + s0t);
    features.add("s1t,s0t,b0t=" + s1t + '|' + s0t + '|' + b0t);
    features.add("s0t,b0t,b1t=" + s0t + '|' + b0t + '|' + b1t);
    features.add("s0deps=" + dependents(state, s0));
    features.add("s1deps=" + dependents(state, s1));
    features.add("dist=" + distance(s0, b0));
    return features.toArray(new String[0]);
  }

  private static String word(String[] tokens, int index) {
    if (index == ArcStandardState.ROOT) {
      return ROOT_VALUE;
    }
    return index == ArcStandardState.NONE ? NONE_VALUE : tokens[index];
  }

  private static String tag(String[] tags, int index) {
    if (index == ArcStandardState.ROOT) {
      return ROOT_VALUE;
    }
    return index == ArcStandardState.NONE ? NONE_VALUE : tags[index];
  }

  private static String dependents(ArcStandardState state, int index) {
    return index < 0 ? NONE_VALUE : Integer.toString(Math.min(state.assignedDependents(index), 3));
  }

  private static String distance(int s0, int b0) {
    if (s0 < 0 || b0 < 0) {
      return NONE_VALUE;
    }
    final int distance = b0 - s0;
    return distance >= 4 ? "4+" : Integer.toString(distance);
  }
}
