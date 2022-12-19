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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import morfologik.stemming.DictionaryMetadata;

import opennlp.tools.postag.POSTaggerFactory;
import opennlp.tools.postag.TagDictionary;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.ByteArraySerializer;

/**
 * The factory provides a Morfologik specific {@link POSTaggerFactory} implementation
 * and initializes related resources.
 */
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
   * Initializes a {@link TagDictionary} from a {@link File dictionary file}.
   *
   * @param dictionary The {@link File} used as input for creating the dictionary.
   *
   * @return A valid {@link TagDictionary} ready for use.
   * @throws FileNotFoundException Thrown if {@code dictionary} or related metadata
   *                               could not be read in.
   * @throws IOException Thrown if IO errors occurred.
   */
  public TagDictionary createTagDictionary(File dictionary) throws IOException {

    if (!dictionary.canRead()) {
      throw new FileNotFoundException("Could not read dictionary: " + dictionary.getAbsolutePath());
    }

    Path dictionaryMeta = DictionaryMetadata.getExpectedMetadataLocation(dictionary.toPath());

    if (dictionaryMeta == null || !dictionaryMeta.toFile().canRead()) {
      throw new FileNotFoundException("Could not read dictionary metadata: " + dictionaryMeta.getFileName());
    }

    this.dictData = Files.readAllBytes(dictionary.toPath());
    this.dictInfo = Files.readAllBytes(dictionaryMeta);

    return createMorfologikDictionary(dictData, dictInfo);
  }

  /**
   * @return The {@link TagDictionary} used.
   * @throws RuntimeException Thrown if errors occurred loading or reading
   *                          Morfologik dictionary files.
   */
  @Override
  public TagDictionary getTagDictionary() {
    if (this.dict == null) {

      if (artifactProvider != null) {
        Object obj = artifactProvider.getArtifact(MORFOLOGIK_POSDICT);
        if (obj != null) {
          byte[] data = artifactProvider.getArtifact(MORFOLOGIK_POSDICT);
          byte[] info = artifactProvider.getArtifact(MORFOLOGIK_DICT_INFO);

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
    this.dict = dictionary;
  }

  @Override
  public TagDictionary createEmptyTagDictionary() {
    throw new UnsupportedOperationException(
        "Morfologik POS Tagger factory does not support this operation");
  }

  @Override
  public TagDictionary createTagDictionary(InputStream in)
      throws IOException {
    throw new UnsupportedOperationException(
        "Morfologik POS Tagger factory does not support this operation");
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Map<String, ArtifactSerializer> createArtifactSerializersMap() {
    Map<String, ArtifactSerializer> serializers = super.createArtifactSerializersMap();

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
        .read(new ByteArrayInputStream(data), new ByteArrayInputStream(info));
    return new MorfologikTagDictionary(dict);
  }
}
