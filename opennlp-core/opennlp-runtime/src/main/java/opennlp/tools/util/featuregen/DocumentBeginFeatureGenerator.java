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

import opennlp.tools.commons.ThreadSafe;

/**
 * This feature generator creates document begin features.
 *
 * @see AdaptiveFeatureGenerator
 */
@ThreadSafe
public class DocumentBeginFeatureGenerator implements AdaptiveFeatureGenerator {

  /** First sentence tokens for the document (same role as the former {@code firstSentence} field). */
  private final ThreadLocal<String[]> threadState = new ThreadLocal<>();

  @Override
  public void createFeatures(List<String> features, String[] tokens, int index,
      String[] previousOutcomes) {

    String[] firstSentence = threadState.get();
    if (firstSentence == null) {
      firstSentence = tokens;
      threadState.set(tokens);
    }

    if (firstSentence == tokens && index == 0) {
      features.add("D=begin");
    }
  }

  @Override
  public void clearAdaptiveData() {
    threadState.remove();
  }
}
