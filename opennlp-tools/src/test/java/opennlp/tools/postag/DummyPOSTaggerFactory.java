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

package opennlp.tools.postag;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.UncloseableInputStream;

public class DummyPOSTaggerFactory extends POSTaggerFactory {


  private static final String DUMMY_POSDICT = "DUMMY_POSDICT";
  private DummyPOSDictionary dict;

  public DummyPOSTaggerFactory() {
  }

  public DummyPOSTaggerFactory(Dictionary ngramDictionary, DummyPOSDictionary posDictionary) {
    super(ngramDictionary, null);
    this.dict = posDictionary;
  }

  @Override
  public SequenceValidator<String> getSequenceValidator() {
    return new DummyPOSSequenceValidator();
  }

  @Override
  public DummyPOSDictionary getTagDictionary() {
    return (DummyPOSDictionary) artifactProvider.getArtifact(DUMMY_POSDICT);
  }

  @Override
  public POSContextGenerator getPOSContextGenerator() {
    return new DummyPOSContextGenerator(this.ngramDictionary);
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Map<String, ArtifactSerializer> createArtifactSerializersMap() {
    Map<String, ArtifactSerializer> serializers = super.createArtifactSerializersMap();

    serializers.put(DUMMY_POSDICT, new DummyPOSDictionarySerializer());
    return serializers;
  }

  @Override
  public Map<String, Object> createArtifactMap() {
    Map<String, Object> artifactMap = super.createArtifactMap();
    if (this.dict != null)
      artifactMap.put(DUMMY_POSDICT, this.dict);
    return artifactMap;
  }

  static class DummyPOSContextGenerator extends DefaultPOSContextGenerator {

    public DummyPOSContextGenerator(Dictionary dict) {
      super(dict);
    }

  }

  static class DummyPOSDictionarySerializer implements ArtifactSerializer<DummyPOSDictionary> {

    public DummyPOSDictionary create(InputStream in) throws IOException {
      return DummyPOSDictionary.create(new UncloseableInputStream(in));
    }

    public void serialize(DummyPOSDictionary artifact, OutputStream out)
        throws IOException {
      artifact.serialize(out);
    }
  }

  static class DummyPOSSequenceValidator implements SequenceValidator<String> {

    public boolean validSequence(int i, String[] inputSequence,
        String[] outcomesSequence, String outcome) {
      return true;
    }

  }

  static class DummyPOSDictionary extends POSDictionary {

    private POSDictionary dict;

    public DummyPOSDictionary(POSDictionary dict) {
      this.dict = dict;
    }

    public static DummyPOSDictionary create(
        UncloseableInputStream uncloseableInputStream) throws IOException {
      return new DummyPOSDictionary(POSDictionary.create(uncloseableInputStream));
    }

    public void serialize(OutputStream out) throws IOException {
      dict.serialize(out);
    }

    public String[] getTags(String word) {
      return dict.getTags(word);
    }

  }

}