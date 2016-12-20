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
package opennlp.tools.doccat;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

/**
 * Context generator for document categorizer
 */
class DocumentCategorizerContextGenerator {

  private FeatureGenerator[] mFeatureGenerators;

  DocumentCategorizerContextGenerator(FeatureGenerator... featureGenerators) {
    mFeatureGenerators = featureGenerators;
  }

  public String[] getContext(String text[], Map<String, Object> extraInformation) {

    Collection<String> context = new LinkedList<>();

    for (FeatureGenerator mFeatureGenerator : mFeatureGenerators) {
      Collection<String> extractedFeatures =
          mFeatureGenerator.extractFeatures(text, extraInformation);
      context.addAll(extractedFeatures);
    }

    return context.toArray(new String[context.size()]);
  }
}
