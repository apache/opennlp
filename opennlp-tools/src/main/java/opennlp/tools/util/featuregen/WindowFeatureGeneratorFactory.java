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
 * @see WindowFeatureGenerator
 */
public class WindowFeatureGeneratorFactory
    extends GeneratorFactory.AbstractXmlFeatureGeneratorFactory
    implements GeneratorFactory.XmlFeatureGeneratorFactory {

  public WindowFeatureGeneratorFactory() {
    super();
  }

  @Deprecated // TODO: (OPENNLP-1174) just remove when back-compat is no longer needed
  public AdaptiveFeatureGenerator create(Element generatorElement,
             FeatureGeneratorResourceProvider resourceManager)  throws InvalidFormatException {

    Element nestedGeneratorElement = null;

    NodeList kids = generatorElement.getChildNodes();

    for (int i = 0; i < kids.getLength(); i++) {
      Node childNode = kids.item(i);

      if (childNode instanceof Element) {
        nestedGeneratorElement = (Element) childNode;
        break;
      }
    }

    if (nestedGeneratorElement == null) {
      throw new InvalidFormatException("window feature generator must contain" +
          " an aggregator element");
    }

    AdaptiveFeatureGenerator nestedGenerator =
        GeneratorFactory.createGenerator(nestedGeneratorElement, resourceManager);

    String prevLengthString = generatorElement.getAttribute("prevLength");

    int prevLength;

    try {
      prevLength = Integer.parseInt(prevLengthString);
    } catch (NumberFormatException e) {
      throw new InvalidFormatException("prevLength attribute '" + prevLengthString
          + "' is not a number!", e);
    }

    String nextLengthString = generatorElement.getAttribute("nextLength");

    int nextLength;

    try {
      nextLength = Integer.parseInt(nextLengthString);
    } catch (NumberFormatException e) {
      throw new InvalidFormatException("nextLength attribute '" + nextLengthString
          + "' is not a number!", e);
    }

    return new WindowFeatureGenerator(nestedGenerator, prevLength, nextLength);
  }

  @Deprecated // TODO: (OPENNLP-1174) just remove when back-compat is no longer needed
  static void register(Map<String, GeneratorFactory.XmlFeatureGeneratorFactory> factoryMap) {
    factoryMap.put("window", new WindowFeatureGeneratorFactory());
  }

  @Override
  public AdaptiveFeatureGenerator create() throws InvalidFormatException {
    AdaptiveFeatureGenerator generator = (AdaptiveFeatureGenerator)args.get("generator#0");
    if (generator == null) {
      throw new InvalidFormatException("window feature generator must contain" +
          " an aggregator element");
    }
    return new WindowFeatureGenerator(generator,
        getInt("prevLength"), getInt("nextLength"));
  }
}
