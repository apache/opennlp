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

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.namefind.DictionaryNameFinder;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.DictionarySerializer;

/**
 * The {@link DictionaryFeatureGenerator} uses the {@link DictionaryNameFinder}
 * to generated features for detected names based on the {@link InSpanGenerator}.
 *
 * @see Dictionary
 * @see DictionaryNameFinder
 * @see InSpanGenerator
 */
public class DictionaryFeatureGenerator implements AdaptiveFeatureGenerator {

  private final String dictName;
  private InSpanGenerator isg;

  DictionaryFeatureGenerator(String dictName) {
    this.dictName = dictName;
  }

  public DictionaryFeatureGenerator(Dictionary dict) {
    this("",dict);
  }

  public DictionaryFeatureGenerator(String prefix, Dictionary dict) {
    this(prefix, dict, DictionarySerializer.class.getSimpleName());
  }

  public DictionaryFeatureGenerator(String prefix, Dictionary dict, String dictName) {
    this.dictName = dictName;
    setDictionary(prefix,dict);
  }

  public void setDictionary(Dictionary dict) {
    setDictionary("",dict);
  }

  public void setDictionary(String name, Dictionary dict) {
    isg = new InSpanGenerator(name, new DictionaryNameFinder(dict));
  }

  public void createFeatures(List<String> features, String[] tokens, int index, String[] previousOutcomes) {
    isg.createFeatures(features, tokens, index, previousOutcomes);
  }

  public ArtifactSerializer<?> getArtifactSerializer() {
    return new DictionarySerializer();
  }

  public String getArtifactSerializerName() {
    return dictName;
  }
}
