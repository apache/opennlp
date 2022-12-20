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

import opennlp.tools.postag.POSSample;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

public class MascPOSSampleStream extends FilterObjectStream<MascDocument, POSSample> {

  private MascDocument buffer;
  
  /**
   * Initializes {@link MascPOSSampleStream} from a stream of {@link MascDocument documents}.
   *
   * @param samples A {@link ObjectStream<MascDocument>} of samples.
   * @throws IOException Thrown if none of the documents has POS tags.
   */
  public MascPOSSampleStream(ObjectStream<MascDocument> samples) throws IOException {
    super(samples);
    try {
      do {
        buffer = samples.read();
      } while (!buffer.hasPennTags()); // For now, we'll always use Penn tags
    } catch (Exception e) {
      throw new IOException("None of the documents has POS tags" +
          e.getMessage());
    }
  }
  
  /**
   * Reads the next sample.
   *
   * @return One {@link POSSample sentence together with its POS tags}.
   * @throws IOException Thrown if the sample cannot be extracted.
   */
  @Override
  public POSSample read() throws IOException {

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
        if (buffer.hasPennTags()) {
          sentence = buffer.read();
        }
      }

      List<String> tokens = sentence.getTokenStrings();
      List<String> POStags = sentence.getTags();
      return new POSSample(tokens, POStags);

    } catch (IOException e) {
      throw new IOException("Could not get a sample of POS tags from the data.");
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
