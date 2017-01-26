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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.SerializableArtifact;

public class WordClusterDictionary implements SerializableArtifact {

  public static class WordClusterDictionarySerializer implements ArtifactSerializer<WordClusterDictionary> {

    public WordClusterDictionary create(InputStream in) throws IOException {
      return new WordClusterDictionary(in);
    }

    public void serialize(WordClusterDictionary artifact, OutputStream out) throws IOException {
      artifact.serialize(out);
    }
  }

  private Map<String, String> tokenToClusterMap = new HashMap<>();

  /**
   * Read word2vec and clark clustering style lexicons.
   * @param in the inputstream
   * @throws IOException the io exception
   */
  public WordClusterDictionary(InputStream in) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    String line;
    while ((line = reader.readLine()) != null) {
      String parts[] = line.split(" ");
      if (parts.length == 3) {
        tokenToClusterMap.put(parts[0], parts[1].intern());
      } else if (parts.length == 2) {
        tokenToClusterMap.put(parts[0], parts[1].intern());
      }
    }
  }

  public String lookupToken(String string) {
    return tokenToClusterMap.get(string);
  }

  public void serialize(OutputStream out) throws IOException {
    Writer writer = new BufferedWriter(new OutputStreamWriter(out));

    for (Map.Entry<String, String> entry : tokenToClusterMap.entrySet()) {
      writer.write(entry.getKey() + " " + entry.getValue() + "\n");
    }

    writer.flush();
  }

  public Class<?> getArtifactSerializerClass() {
    return WordClusterDictionarySerializer.class;
  }
}
