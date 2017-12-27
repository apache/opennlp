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

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.ArtifactSerializer;

/**
 * Generates Brown clustering features for token classes.
 */
public class BrownClusterTokenClassFeatureGeneratorFactory
    extends GeneratorFactory.AbstractXmlFeatureGeneratorFactory
    implements GeneratorFactory.XmlFeatureGeneratorFactory {

  public BrownClusterTokenClassFeatureGeneratorFactory() {
    super();
  }

  @Deprecated // TODO: (OPENNLP-1174) just remove when back-compat is no longer needed
  public AdaptiveFeatureGenerator create(Element generatorElement,
             FeatureGeneratorResourceProvider resourceManager) throws InvalidFormatException {

    String dictResourceKey = generatorElement.getAttribute("dict");

    Object dictResource = resourceManager.getResource(dictResourceKey);


    if (!(dictResource instanceof BrownCluster)) {
      throw new InvalidFormatException("Not a BrownLexicon resource for key: " + dictResourceKey);
    }

    return new BrownTokenClassFeatureGenerator((BrownCluster) dictResource);
  }

  @Deprecated // TODO: (OPENNLP-1174) just remove when back-compat is no longer needed
  static void register(Map<String, GeneratorFactory.XmlFeatureGeneratorFactory> factoryMap) {
    factoryMap.put("brownclustertokenclass", new BrownClusterTokenClassFeatureGeneratorFactory());
  }

  @Override
  public AdaptiveFeatureGenerator create() throws InvalidFormatException {
    // if resourceManager is null, we don't instantiate
    if (resourceManager == null)
      return null;

    String dictResourceKey = getStr("dict");
    Object dictResource = resourceManager.getResource(dictResourceKey);
    if (!(dictResource instanceof BrownCluster)) {
      throw new InvalidFormatException("Not a BrownLexicon resource for key: " + dictResourceKey);
    }

    return new BrownTokenClassFeatureGenerator((BrownCluster) dictResource);
  }

  @Override
  public Map<String, ArtifactSerializer<?>> getArtifactSerializerMapping() throws InvalidFormatException {
    Map<String, ArtifactSerializer<?>> mapping = new HashMap<>();
    mapping.put(getStr("dict"), new BrownCluster.BrownClusterSerializer());
    return mapping;
  }
}
