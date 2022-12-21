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

package opennlp.tools.formats.masc;

import java.io.IOException;
import java.util.List;

import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

public class MascNamedEntitySampleStream extends FilterObjectStream<MascDocument, NameSample> {

  private MascDocument buffer;

  /**
   * Initializes {@link MascNamedEntitySampleStream} from a stream of {@link MascDocument documents}.
   *
   * @param samples A {@link ObjectStream<MascDocument>} of samples.
   * @throws IOException Thrown if none of the documents has NE labels.
   */
  public MascNamedEntitySampleStream(ObjectStream<MascDocument> samples) throws IOException {
    super(samples);
    try {
      do {
        buffer = samples.read();
      } while (!buffer.hasNamedEntities());
    } catch (Exception e) {
      throw new IOException("None of the documents has named entity labels" +
          e.getMessage());
    }
  }

  /**
   * Reads the next sample of named entities.
   *
   * @return One {@link NameSample sentence together with its named entity annotation}.
   * @throws IOException Thrown if the sample cannot be extracted
   */
  @Override
  public NameSample read() throws IOException {

    /*
     * Read the documents one sentence at a time
     * If the document is over, move to the next one
     * If both document stream and sentence stream are over, return null
     */
    try {
      MascSentence sentence = buffer.read();
      while (sentence == null) {
        buffer = samples.read();
        if (buffer == null) {
          return null;
        }
        if (buffer.hasNamedEntities()) {
          sentence = buffer.read();
        }
      }

      List<String> tokens = sentence.getTokenStrings();
      String[] tokensArray = new String[tokens.size()];
      tokens.toArray(tokensArray);

      List<Span> namedEntities = sentence.getNamedEntities();
      Span[] namedEntitiesArray = new Span[namedEntities.size()];
      namedEntities.toArray(namedEntitiesArray);

      // TODO: should the user decide about clearAdaptiveData?
      return new NameSample(tokensArray, namedEntitiesArray, true);

    } catch (IOException e) {
      throw new IOException("Could not get a sample of named entities from the data.");
    }
  }

  @Override
  public void close() throws IOException {
    samples.close();
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    samples.reset();
    buffer = samples.read();
  }
}

