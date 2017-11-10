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


import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import opennlp.tools.util.InvalidFormatException;

/**
 * @see CachedFeatureGenerator
 */
public class CachedFeatureGeneratorFactory
    extends GeneratorFactory.AbstractXmlFeatureGeneratorFactory
    implements GeneratorFactory.XmlFeatureGeneratorFactory {

  public CachedFeatureGeneratorFactory() {
    super();
  }

  @Deprecated // TODO: (OPENNLP-1174) just remove when back-compat is no longer needed
  public AdaptiveFeatureGenerator create(Element generatorElement,
             FeatureGeneratorResourceProvider resourceManager) throws InvalidFormatException {

    Element cachedGeneratorElement = null;

    NodeList kids = generatorElement.getChildNodes();

    for (int i = 0; i < kids.getLength(); i++) {
      Node childNode = kids.item(i);

      if (childNode instanceof Element) {
        cachedGeneratorElement = (Element) childNode;
        break;
      }
    }

    if (cachedGeneratorElement == null) {
      throw new InvalidFormatException("Could not find containing generator element!");
    }

    AdaptiveFeatureGenerator cachedGenerator =
        GeneratorFactory.createGenerator(cachedGeneratorElement, resourceManager);

    return new CachedFeatureGenerator(cachedGenerator);
  }

  @Deprecated // TODO: (OPENNLP-1174) just remove when back-compat is no longer needed
  static void register(Map<String, GeneratorFactory.XmlFeatureGeneratorFactory> factoryMap) {
    factoryMap.put("cache", new CachedFeatureGeneratorFactory());
  }

  @Override
  public AdaptiveFeatureGenerator create() throws InvalidFormatException {
    AdaptiveFeatureGenerator generator = (AdaptiveFeatureGenerator)args.get("generator#0");
    if (generator == null) {
      throw new InvalidFormatException("Could not find containing generator element!");
    }
    return new CachedFeatureGenerator(generator);
  }
}
