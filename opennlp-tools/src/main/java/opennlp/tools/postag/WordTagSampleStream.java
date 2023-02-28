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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;

/**
 * A stream filter which reads a sentence per line which contains
 * words and tags in {@code word_tag} format and outputs a {@link POSSample} objects.
 */
public class WordTagSampleStream extends FilterObjectStream<String, POSSample> {

  private static final Logger logger = LoggerFactory.getLogger(WordTagSampleStream.class);

  /**
   * Initializes a {@link POSSample} instance.
   *
   * @param sentences The {@link ObjectStream sentences} to wrap.
   */
  public WordTagSampleStream(ObjectStream<String> sentences) {
    super(sentences);
  }

  /**
   * Parses the next sentence and return the next {@link POSSample} object.
   * <p>
   * If an error occurs an empty {@link POSSample} object is returned
   * and a warning message is logged. Usually it does not matter if one
   * or many sentences are ignored.
   *
   * @return A valid {@link POSSample} or {@code null} if the
   *         {@link ObjectStream sentence stream} is exhausted.
   *
   * @throws IOException Thrown if IO errors occurred during read.
   */
  @Override
  public POSSample read() throws IOException {

    String sentence = samples.read();

    if (sentence != null) {
      POSSample sample;
      try {
        sample = POSSample.parse(sentence);
      } catch (InvalidFormatException e) {
        // TODO: An exception in error case should be thrown.
        logger.warn("Error during parsing, ignoring sentence: {}", sentence, e);

        sample = new POSSample(new String[]{}, new String[]{});
      }

      return sample;
    }
    else {
      // sentences stream is exhausted
      return null;
    }
  }
}
