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

package opennlp.tools.sentdetect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import opennlp.model.Event;
import opennlp.tools.util.AbstractEventStream;
import opennlp.tools.util.Span;

public class SDEventStreamNew extends AbstractEventStream<SentenceSample> {

  private SDContextGenerator cg;
  private EndOfSentenceScanner scanner;
  
  /**
   * Initializes the current instance.
   * 
   * @param samples
   */
  public SDEventStreamNew(Iterator<SentenceSample> samples) {
    super(samples);
    
    scanner = new opennlp.tools.lang.english.EndOfSentenceScanner();
    cg = new DefaultSDContextGenerator(
        opennlp.tools.lang.english.EndOfSentenceScanner.eosCharacters);
  }
  
  @Override
  protected Iterator<Event> createEvents(SentenceSample sample) {

    Collection<Event> events = new ArrayList<Event>();

    for (Iterator<Integer> it = scanner.getPositions(
        sample.getDocument()).iterator(); it.hasNext();) {

      int candidate = it.next();

      String type = SentenceDetectorME.NO_SPLIT;

      for (Span sentenceSpan : sample.getSentences()) {
        if (candidate == sentenceSpan.getEnd()) {
          type = SentenceDetectorME.SPLIT;
          break;
        }
      }

      events.add(new Event(type, cg.getContext(sample.getDocument(), candidate)));
    }

    return events.iterator();
  }
}