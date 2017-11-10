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
import java.util.Objects;

import org.w3c.dom.Element;

import opennlp.tools.util.InvalidFormatException;

/**
 * @see TokenClassFeatureGenerator
 */
public class TokenClassFeatureGeneratorFactory
    extends GeneratorFactory.AbstractXmlFeatureGeneratorFactory
    implements GeneratorFactory.XmlFeatureGeneratorFactory {

  public TokenClassFeatureGeneratorFactory() {
    super();
  }

  @Deprecated // TODO: (OPENNLP-1174) just remove when back-compat is no longer needed
  public AdaptiveFeatureGenerator create(Element generatorElement,
             FeatureGeneratorResourceProvider resourceManager) {

    String attribute = generatorElement.getAttribute("wordAndClass");

    // Default to true.
    boolean generateWordAndClassFeature = true;

    if (!Objects.equals(attribute, "")) {
      // Anything other than "true" sets it to false.
      if (!"true".equalsIgnoreCase(attribute)) {
        generateWordAndClassFeature = false;
      }
    }

    return new TokenClassFeatureGenerator(generateWordAndClassFeature);
  }

  @Deprecated // TODO: (OPENNLP-1174) just remove when back-compat is no longer needed
  static void register(Map<String, GeneratorFactory.XmlFeatureGeneratorFactory> factoryMap) {
    factoryMap.put("tokenclass", new TokenClassFeatureGeneratorFactory());
  }

  @Override
  public AdaptiveFeatureGenerator create() throws InvalidFormatException {
    return new TokenClassFeatureGenerator(getBool("wordAndClass", true));
  }
}
