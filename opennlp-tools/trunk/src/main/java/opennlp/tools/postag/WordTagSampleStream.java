/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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
import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ParseException;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * A stream filter which reads a sentence per line which contains
 * words and tags in word_tag format and outputs a {@link POSSample} objects.
 */
public class WordTagSampleStream implements ObjectStream<POSSample> {

  private static Logger logger = Logger.getLogger(WordTagSampleStream.class.getName());

  private ObjectStream<String> sentences;

  /**
   * Initializes the current instance.
   *
   * @param sentences
   */
  public WordTagSampleStream(Reader sentences) throws IOException {

    if (sentences == null)
      throw new IllegalArgumentException("sentences must not be null!");

    this.sentences = new PlainTextByLineStream(sentences);
  }

  public WordTagSampleStream(ObjectStream<String> sentences) {
    if (sentences == null)
      throw new IllegalArgumentException("sentences must not be null!");

    this.sentences = sentences;
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

    String sentence = sentences.read();

    if (sentence != null) {
      POSSample sample;
      try {
        sample = POSSample.parse(sentence);
      } catch (ParseException e) {
  
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

  public void reset() throws IOException {
    sentences.reset();
  }
  
  public void close() throws IOException {
    sentences.close();
  }
}
