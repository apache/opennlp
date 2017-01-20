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

package opennlp.tools.chunker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * Parses the conll 2000 shared task shallow parser training data.
 * <p>
 * Data format is specified on the conll page:<br>
 * <a href="http://www.cnts.ua.ac.be/conll2000/chunking/">
 * http://www.cnts.ua.ac.be/conll2000/chunking/</a>
 */
public class ChunkSampleStream extends FilterObjectStream<String, ChunkSample> {

  /**
   * Initializes the current instance.
   *
   * @param samples a plain text line stream
   */
  public ChunkSampleStream(ObjectStream<String> samples) {
    super(samples);
  }

  public ChunkSample read() throws IOException {

    List<String> toks = new ArrayList<>();
    List<String> tags = new ArrayList<>();
    List<String> preds = new ArrayList<>();

    for (String line = samples.read(); line != null && !line.equals(""); line = samples.read()) {
      String[] parts = line.split(" ");
      if (parts.length != 3) {
        System.err.println("Skipping corrupt line: " + line);
      }
      else {
        toks.add(parts[0]);
        tags.add(parts[1]);
        preds.add(parts[2]);
      }
    }

    if (toks.size() > 0) {
      return new ChunkSample(toks.toArray(new String[toks.size()]),
          tags.toArray(new String[tags.size()]), preds.toArray(new String[preds.size()]));
    }

    return null;
  }
}
