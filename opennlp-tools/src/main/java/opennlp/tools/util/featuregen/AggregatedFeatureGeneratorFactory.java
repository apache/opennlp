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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import opennlp.tools.util.InvalidFormatException;

/**
 * @see AggregatedFeatureGenerator
 */
public class AggregatedFeatureGeneratorFactory
    extends GeneratorFactory.AbstractXmlFeatureGeneratorFactory
    implements GeneratorFactory.XmlFeatureGeneratorFactory {

  public AggregatedFeatureGeneratorFactory() {
    super();
  }

  @Deprecated // TODO: (OPENNLP-1174) just remove when back-compat is no longer needed
  public AdaptiveFeatureGenerator create(Element generatorElement,
             FeatureGeneratorResourceProvider resourceManager)  throws InvalidFormatException {

    Collection<AdaptiveFeatureGenerator> aggregatedGenerators = new LinkedList<>();

    NodeList childNodes = generatorElement.getChildNodes();

    for (int i = 0; i < childNodes.getLength(); i++) {
      Node childNode = childNodes.item(i);
      if (childNode instanceof Element) {
        Element aggregatedGeneratorElement = (Element) childNode;
        aggregatedGenerators.add(
            GeneratorFactory.createGenerator(aggregatedGeneratorElement, resourceManager));
      }
    }

    return new AggregatedFeatureGenerator(aggregatedGenerators.toArray(
        new AdaptiveFeatureGenerator[aggregatedGenerators.size()]));
  }

  @Deprecated // TODO: (OPENNLP-1174) just remove when back-compat is no longer needed
  static void register(Map<String, GeneratorFactory.XmlFeatureGeneratorFactory> factoryMap) {
    factoryMap.put("generators", new AggregatedFeatureGeneratorFactory());
  }

  @Override
  public AdaptiveFeatureGenerator create() throws InvalidFormatException {
    List<AdaptiveFeatureGenerator> aggregatedGenerators = new ArrayList<>();
    for (Map.Entry<String, Object> arg: args.entrySet()) {
      if (arg.getKey().startsWith("generator#")) {
        aggregatedGenerators.add((AdaptiveFeatureGenerator)arg.getValue());
      }
    }
    return new AggregatedFeatureGenerator(aggregatedGenerators.toArray(
        new AdaptiveFeatureGenerator[aggregatedGenerators.size()]));
  }
}
