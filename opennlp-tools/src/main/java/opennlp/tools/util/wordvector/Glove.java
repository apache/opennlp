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

package opennlp.tools.util.wordvector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.util.java.Experimental;

/**
 * <p>
 * Warning: Experimental new feature, see OPENNLP-1144 for details, the API might be changed anytime.
 */
@Experimental
public class Glove {

  private Glove() {
  }

  /**
   * Parses a glove vector plain text file.
   * <p>
   * Warning: Experimental new feature, see OPENNLP-1144 for details, the API might be changed anytime.
   *
   * @param in
   * @return
   * @throws IOException
   */
  @Experimental
  public static WordVectorTable parse(InputStream in) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8),
        1024 * 1024);

    Map<String, WordVector> vectors = new HashMap<>();

    int dimension = -1;
    String line;
    while ((line = reader.readLine()) != null) {
      String[] parts = line.split(" ");

      if (dimension == -1) {
        dimension = parts.length - 1;
      }
      else if (dimension != parts.length - 1) {
        throw new IOException("Vector dimension must be constant!");
      }

      String token = parts[0];

      float[] vector = new float[dimension];

      for (int i = 0; i < vector.length; i++) {
        vector[i] = Float.parseFloat(parts[i + 1]);
      }

      vectors.put(token, new FloatArrayVector(vector));
    }

    return new MapWordVectorTable(Collections.unmodifiableMap(vectors));
  }
}
