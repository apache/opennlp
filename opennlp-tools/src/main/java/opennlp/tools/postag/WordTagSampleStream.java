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
import java.util.logging.Level;
import java.util.logging.Logger;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;

/**
 * A stream filter which reads a sentence per line which contains
 * words and tags in word_tag format and outputs a {@link POSSample} objects.
 */
public class WordTagSampleStream extends FilterObjectStream<String, POSSample> {

  private static Logger logger = Logger.getLogger(WordTagSampleStream.class.getName());

  /**
   * Initializes the current instance.
   *
   * @param sentences the sentences
   */
  public WordTagSampleStream(ObjectStream<String> sentences) {
    super(sentences);
  }

  /**
   * Parses the next sentence and return the next
   * {@link POSSample} object.
   *
   * If an error occurs an empty {@link POSSample} object is returned
   * and an warning message is logged. Usually it does not matter if one
   * of many sentences is ignored.
   *
   * TODO: An exception in error case should be thrown.
   */
  public POSSample read() throws IOException {

    String sentence = samples.read();

    if (sentence != null) {
      POSSample sample;
      try {
        sample = POSSample.parse(sentence);
      } catch (InvalidFormatException e) {

        if (logger.isLoggable(Level.WARNING)) {
          logger.warning("Error during parsing, ignoring sentence: " + sentence);
        }

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
