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

package opennlp.morfologik.tagdict;

import static opennlp.morfologik.util.MorfologikUtil.getExpectedPropertiesFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.postag.POSTaggerFactory;
import opennlp.tools.postag.TagDictionary;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.ModelUtil;

public class MorfologikPOSTaggerFactory extends POSTaggerFactory {

  private static final String MORFOLOGIK_POSDICT_SUF = "morfologik_dict";
  private static final String MORFOLOGIK_DICT_INFO_SUF = "morfologik_info";

  private static final String MORFOLOGIK_POSDICT = "tagdict."
      + MORFOLOGIK_POSDICT_SUF;
  private static final String MORFOLOGIK_DICT_INFO = "tagdict."
      + MORFOLOGIK_DICT_INFO_SUF;

  private TagDictionary dict;

  private byte[] dictInfo;
  private byte[] dictData;

  public MorfologikPOSTaggerFactory() {
  }

  /**
   * Creates a new {@link POSTaggerFactory} that uses the a Morfologik based {@link TagDictionary}.
   * 
   * @param ngramDictionary a ngramDictionary 
   * @param morfologikDictionary a Morfologik dictionary
   * @param morfologikDictionaryMetadata the dictionary metadata
   * @throws IOException invalid Morfologik dictionary
   */
  public MorfologikPOSTaggerFactory(Dictionary ngramDictionary,
      byte[] morfologikDictionary, byte[] morfologikDictionaryMetadata) throws IOException {
    super(ngramDictionary, null);
    this.dictData = morfologikDictionary;
    this.dictInfo = morfologikDictionaryMetadata;
    
    this.dict = createMorfologikDictionary(dictData, dictInfo);
  }

  @Override
  protected void init(Dictionary ngramDictionary, TagDictionary posDictionary) {
    super.init(ngramDictionary, null);
    this.dict = posDictionary;

    // get the dictionary path
    String path = System.getProperty("morfologik.dict");
    if (path == null) {
      throw new IllegalArgumentException(
          "The property fsa.dict is missing! -Dmorfologik.dict=path");
    }

    // now we try to load it...
    try {
      this.dictData = Files.readAllBytes(Paths.get(path));
      this.dictInfo = Files.readAllBytes(getExpectedPropertiesFile(path)
          .toPath());

      this.dict = createMorfologikDictionary(dictData, dictInfo);

    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "The file is not a Morfologik dictionary!", e);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Could not open the Morfologik dictionary or the .info file", e);
    }
  }

  @Override
  public TagDictionary getTagDictionary() {
    if (this.dict == null) {

      if (artifactProvider != null) {
        Object obj = artifactProvider.getArtifact(MORFOLOGIK_POSDICT);
        if (obj != null) {
          byte[] data = (byte[]) artifactProvider
              .getArtifact(MORFOLOGIK_POSDICT);
          byte[] info = (byte[]) artifactProvider
              .getArtifact(MORFOLOGIK_DICT_INFO);

          try {
            this.dict = createMorfologikDictionary(data, info);
          } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                "Could not load the dictionary files to Morfologik.", e);
          } catch (IOException e) {
            throw new RuntimeException(
                "IO error while reading the Morfologik dictionary files.", e);
          }
        }
      }
    }

    return this.dict;
  }

  @Override
  public void setTagDictionary(TagDictionary dictionary) {
    throw new UnsupportedOperationException(
        "Morfologik POS Tagger factory does not support this operation");
  }

  @Override
  public TagDictionary createEmptyTagDictionary() {
    throw new UnsupportedOperationException(
        "Morfologik POS Tagger factory does not support this operation");
  }

  @Override
  public TagDictionary createTagDictionary(File dictionary)
      throws InvalidFormatException, FileNotFoundException, IOException {
    throw new UnsupportedOperationException(
        "Morfologik POS Tagger factory does not support this operation");
  }

  @Override
  public TagDictionary createTagDictionary(InputStream in)
      throws InvalidFormatException, IOException {
    throw new UnsupportedOperationException(
        "Morfologik POS Tagger factory does not support this operation");
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Map<String, ArtifactSerializer> createArtifactSerializersMap() {
    Map<String, ArtifactSerializer> serializers = super
        .createArtifactSerializersMap();

    serializers.put(MORFOLOGIK_POSDICT_SUF, new ByteArraySerializer());
    serializers.put(MORFOLOGIK_DICT_INFO_SUF, new ByteArraySerializer());

    return serializers;
  }

  @Override
  public Map<String, Object> createArtifactMap() {
    Map<String, Object> artifactMap = super.createArtifactMap();
    artifactMap.put(MORFOLOGIK_POSDICT, this.dictData);
    artifactMap.put(MORFOLOGIK_DICT_INFO, this.dictInfo);
    return artifactMap;
  }

  private TagDictionary createMorfologikDictionary(byte[] data, byte[] info)
      throws IOException {
    morfologik.stemming.Dictionary dict = morfologik.stemming.Dictionary
        .read(new ByteArrayInputStream(data), new ByteArrayInputStream(
            info));
    return new MorfologikTagDictionary(dict);
  }

  static class ByteArraySerializer implements ArtifactSerializer<byte[]> {

    public byte[] create(InputStream in) throws IOException,
        InvalidFormatException {

      return ModelUtil.read(in);
    }

    public void serialize(byte[] artifact, OutputStream out) throws IOException {
      out.write(artifact);
    }
  }

}
