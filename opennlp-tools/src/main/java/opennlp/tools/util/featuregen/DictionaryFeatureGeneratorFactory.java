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

import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Element;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.DictionarySerializer;

/**
 * @see DictionaryFeatureGenerator
 */
public class DictionaryFeatureGeneratorFactory
    extends GeneratorFactory.AbstractXmlFeatureGeneratorFactory
    implements GeneratorFactory.XmlFeatureGeneratorFactory {

  public DictionaryFeatureGeneratorFactory() {
    super();
  }

  @Deprecated // TODO: (OPENNLP-1174) just remove when back-compat is no longer needed
  public AdaptiveFeatureGenerator create(Element generatorElement,
             FeatureGeneratorResourceProvider resourceManager) throws InvalidFormatException {

    String dictResourceKey = generatorElement.getAttribute("dict");

    Object dictResource = resourceManager.getResource(dictResourceKey);

    if (!(dictResource instanceof Dictionary)) {
      throw new InvalidFormatException("No dictionary resource for key: " + dictResourceKey);
    }

    String prefix = generatorElement.getAttribute("prefix");

    return new DictionaryFeatureGenerator(prefix, (Dictionary) dictResource);
  }

  @Deprecated // TODO: (OPENNLP-1174) just remove when back-compat is no longer needed
  static void register(Map<String, GeneratorFactory.XmlFeatureGeneratorFactory> factoryMap) {
    factoryMap.put("dictionary", new DictionaryFeatureGeneratorFactory());
  }

  @Override
  public AdaptiveFeatureGenerator create() throws InvalidFormatException {
    // if resourceManager is null, we don't instantiate
    if (resourceManager == null)
      return null;

    String dictResourceKey = getStr("dict");
    Object dictResource = resourceManager.getResource(dictResourceKey);
    if (!(dictResource instanceof Dictionary)) {
      throw new InvalidFormatException("No dictionary resource for key: " + dictResourceKey);
    }

    return new DictionaryFeatureGenerator(getStr("prefix"), (Dictionary) dictResource);
  }

  @Override
  public Map<String, ArtifactSerializer<?>> getArtifactSerializerMapping() throws InvalidFormatException {
    Map<String, ArtifactSerializer<?>> mapping = new HashMap<>();
    mapping.put(getStr("dict"), new DictionarySerializer());
    return mapping;
  }
}
