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

package opennlp.tools.sentdetect;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.model.ArtifactSerializer;

public class DummySentenceDetectorFactory extends SentenceDetectorFactory {

  private static final String DUMMY_DICT = "dummy";
  private DummyDictionary dict;

  public DummySentenceDetectorFactory() {
  }

  public DummySentenceDetectorFactory(String languageCode, boolean useTokenEnd,
      Dictionary abbreviationDictionary, char[] eosCharacters) {
    super(languageCode, useTokenEnd, abbreviationDictionary, eosCharacters);
  }

  @Override
  protected void init(String languageCode, boolean useTokenEnd,
      Dictionary abbreviationDictionary, char[] eosCharacters) {
    super.init(languageCode, useTokenEnd, abbreviationDictionary, eosCharacters);
    this.dict = new DummyDictionary(abbreviationDictionary);
  }

  @Override
  public DummyDictionary getAbbreviationDictionary() {
    if (this.dict == null && artifactProvider != null) {
      this.dict = artifactProvider.getArtifact(DUMMY_DICT);
    }
    return this.dict;
  }

  @Override
  public SDContextGenerator getSDContextGenerator() {
    return new DummySDContextGenerator(getAbbreviationDictionary()
        .asStringSet(), getEOSCharacters());
  }

  @Override
  public EndOfSentenceScanner getEndOfSentenceScanner() {
    return new DummyEOSScanner(getEOSCharacters());
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Map<String, ArtifactSerializer> createArtifactSerializersMap() {
    Map<String, ArtifactSerializer> serializers = super.createArtifactSerializersMap();
    serializers.put(DUMMY_DICT, new DummyDictionarySerializer());
    return serializers;
  }

  @Override
  public Map<String, Object> createArtifactMap() {
    Map<String, Object> artifactMap = super.createArtifactMap();
    if (this.dict != null) {
      artifactMap.put(DUMMY_DICT, this.dict);
    }
    return artifactMap;
  }

  public static class DummyDictionarySerializer implements ArtifactSerializer<DummyDictionary> {

    public DummyDictionary create(InputStream in) throws IOException {
      return new DummyDictionary(in);
    }

    public void serialize(DummyDictionary artifact, OutputStream out)
        throws IOException {
      artifact.serialize(out);
    }
  }

  public static class DummyDictionary extends Dictionary {
    private Dictionary indict;

    public DummyDictionary(Dictionary dict) {
      this.indict = dict;
    }

    public DummyDictionary(InputStream in) throws IOException {
      this.indict = new Dictionary(in);
    }

    public void serialize(OutputStream out) throws IOException {
      indict.serialize(out);
    }

    public Set<String> asStringSet() {
      return indict.asStringSet();
    }

    @Override
    public Class<?> getArtifactSerializerClass() {
      return DummyDictionarySerializer.class;
    }
  }

  static class DummySDContextGenerator extends DefaultSDContextGenerator {

    public DummySDContextGenerator(Set<String> inducedAbbreviations,
        char[] eosCharacters) {
      super(inducedAbbreviations, eosCharacters);
    }

  }

  static class DummyEOSScanner extends DefaultEndOfSentenceScanner {

    public DummyEOSScanner(char[] eosCharacters) {
      super(eosCharacters);
    }

  }

}
