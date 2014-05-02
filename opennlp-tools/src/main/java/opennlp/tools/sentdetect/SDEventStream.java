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

package opennlp.tools.sentdetect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import opennlp.tools.ml.model.Event;
import opennlp.tools.util.AbstractEventStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

public class SDEventStream extends AbstractEventStream<SentenceSample> {

  private SDContextGenerator cg;
  private EndOfSentenceScanner scanner;

  /**
   * Initializes the current instance.
   *
   * @param samples
   */
  public SDEventStream(ObjectStream<SentenceSample> samples, SDContextGenerator cg,
      EndOfSentenceScanner scanner) {
    super(samples);

    this.cg = cg;
    this.scanner = scanner;
  }

  @Override
  protected Iterator<Event> createEvents(SentenceSample sample) {

    Collection<Event> events = new ArrayList<Event>();

    for (Span sentenceSpan : sample.getSentences()) {
      String sentenceString = sentenceSpan.getCoveredText(sample.getDocument()).toString();

      for (Iterator<Integer> it = scanner.getPositions(
          sentenceString).iterator(); it.hasNext();) {

        int candidate = it.next();
        String type = SentenceDetectorME.NO_SPLIT;
        if (!it.hasNext()) {
          type = SentenceDetectorME.SPLIT;
        }

        events.add(new Event(type, cg.getContext(sample.getDocument(),
            sentenceSpan.getStart() + candidate)));
      }
    }


    return events.iterator();
  }
}
