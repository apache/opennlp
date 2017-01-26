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
import java.util.regex.Pattern;

import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.SerializableArtifact;

/**
 *
 * Class to load a Brown cluster document: word\tword_class\tprob
 * http://metaoptimize.com/projects/wordreprs/
 *
 * The file containing the clustering lexicon has to be passed as the
 * value of the dict attribute of each BrownCluster feature generator.
 *
 */
public class BrownCluster implements SerializableArtifact {

  private static final Pattern tabPattern = Pattern.compile("\t");

  public static class BrownClusterSerializer implements ArtifactSerializer<BrownCluster> {

    public BrownCluster create(InputStream in) throws IOException {
      return new BrownCluster(in);
    }

    public void serialize(BrownCluster artifact, OutputStream out)
        throws IOException {
      artifact.serialize(out);
    }
  }

  private Map<String, String> tokenToClusterMap = new HashMap<>();

  /**
   * Generates the token to cluster map from Brown cluster input file.
   * NOTE: we only add those tokens with frequency bigger than 5.
   * @param in the inputstream
   * @throws IOException the io exception
   */
  public BrownCluster(InputStream in) throws IOException {

    BufferedReader breader =
        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    String line;
    while ((line = breader.readLine()) != null) {
      String[] lineArray = tabPattern.split(line);
      if (lineArray.length == 3) {
        int freq = Integer.parseInt(lineArray[2]);
        if (freq > 5 ) {
          tokenToClusterMap.put(lineArray[1], lineArray[0]);
        }
      }
      else if (lineArray.length == 2) {
        tokenToClusterMap.put(lineArray[0], lineArray[1]);
      }
    }
  }

  /**
   * Check if a token is in the Brown:paths, token map.
   * @param string the token to look-up
   * @return the brown class if such token is in the brown cluster map
   */
  public String lookupToken(String string) {
    return tokenToClusterMap.get(string);
  }

  public void serialize(OutputStream out) throws IOException {
    Writer writer = new BufferedWriter(new OutputStreamWriter(out));

    for (Map.Entry<String, String> entry : tokenToClusterMap.entrySet()) {
      writer.write(entry.getKey() + "\t" + entry.getValue() + "\n");
    }
    writer.flush();
  }

  public Class<?> getArtifactSerializerClass() {
    return BrownClusterSerializer.class;
  }
}
