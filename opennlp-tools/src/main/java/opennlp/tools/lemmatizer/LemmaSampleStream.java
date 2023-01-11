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

package opennlp.tools.lemmatizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * Reads data for training and testing the {@link Lemmatizer}.
 * <p>
 * The format consists of:
 * {@code word\tpostag\tlemma}.
 */
public class LemmaSampleStream extends FilterObjectStream<String, LemmaSample> {

  private static final Logger logger = LoggerFactory.getLogger(LemmaSampleStream.class);

  /**
   * Initializes a {@link LemmaSampleStream instance}.
   *
   * @param samples A plain text {@link ObjectStream line stream}.
   */
  public LemmaSampleStream(ObjectStream<String> samples) {
    super(samples);
  }

  @Override
  public LemmaSample read() throws IOException {

    List<String> toks = new ArrayList<>();
    List<String> tags = new ArrayList<>();
    List<String> preds = new ArrayList<>();

    for (String line = samples.read(); line != null && !line.equals(""); line = samples.read()) {
      String[] parts = line.split("\t");
      if (parts.length != 3) {
        logger.warn("Skipping corrupt line: {}", line);
      }
      else {
        toks.add(parts[0]);
        tags.add(parts[1]);
        preds.add(parts[2]);
      }
    }
    if (toks.size() > 0) {
      return new LemmaSample(toks.toArray(new String[0]), tags.toArray(new String[0]),
          preds.toArray(new String[0]));
    }
    else {
      return null;
    }
  }
}
