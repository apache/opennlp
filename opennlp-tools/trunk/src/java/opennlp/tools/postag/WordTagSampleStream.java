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

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import opennlp.maxent.DataStream;
import opennlp.tools.util.ParseException;

/**
 * The {@link WordTagSampleStream} reads a sentence which
 * contains tags in the word_tag format and outputs a {@link POSSample}
 * object.
 */
public class WordTagSampleStream implements Iterator<POSSample> {
  
  private static Logger logger = Logger.getLogger(WordTagSampleStream.class.getName());
  
  private DataStream sentences;
  
  /**
   * Initializes the current instance.
   * 
   * @param sentences
   */
  public WordTagSampleStream(DataStream sentences) {
    this.sentences = sentences;
  }
  
  public boolean hasNext() {
    return sentences.hasNext();
  }
  
  /**
   * Parses the next sentence and return the next 
   * {@link POSSample} object.
   * 
   * If an error occurs an empty {@link POSSample} object is returned
   * and an warning message is logged. Usually it does not matter if one 
   * of many sentences is ignored. 
   */
  public POSSample next() {
    
    String sentence = (String) sentences.nextToken();
    
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

  public void remove() {
    throw new UnsupportedOperationException();
  }
}