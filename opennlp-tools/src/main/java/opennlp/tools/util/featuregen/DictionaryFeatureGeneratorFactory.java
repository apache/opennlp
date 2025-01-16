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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.DictionarySerializer;

/**
 * A {@link GeneratorFactory} that produces {@link DictionaryFeatureGenerator} instances
 * when {@link #create()} is called.
 *
 * @see DictionaryFeatureGenerator
 */
public class DictionaryFeatureGeneratorFactory
    extends GeneratorFactory.AbstractXmlFeatureGeneratorFactory {

  private static final String DICT = "dict";

  public DictionaryFeatureGeneratorFactory() {
    super();
  }

  @Override
  public AdaptiveFeatureGenerator create() throws InvalidFormatException {
    Dictionary d;
    if (resourceManager == null) { // load the dictionary directly
      String dictResourcePath = getStr(DICT);
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      try (InputStream is = cl.getResourceAsStream(dictResourcePath)) {
        if (is != null) {
          d = ((DictionarySerializer) getArtifactSerializerMapping().get(dictResourcePath)).create(is);
        } else {
          throw new InvalidFormatException("No dictionary resource at: '" + dictResourcePath);
        }
      } catch (IOException e) {
        throw new InvalidFormatException("Error processing resource at: " + dictResourcePath, e);
      }
    } else { // get the dictionary via a resourceManager lookup
      String dictResourceKey = getStr(DICT);
      Object dictResource = resourceManager.getResource(dictResourceKey);
      if (dictResource instanceof Dictionary dict) {
        d = dict;
      } else {
        throw new InvalidFormatException("No dictionary resource for key: " + dictResourceKey);
      }
    }
    return new DictionaryFeatureGenerator(d);
  }

  @Override
  public Map<String, ArtifactSerializer<?>> getArtifactSerializerMapping() throws InvalidFormatException {
    Map<String, ArtifactSerializer<?>> mapping = new HashMap<>();
    mapping.put(getStr(DICT), new DictionarySerializer());
    return mapping;
  }
}
