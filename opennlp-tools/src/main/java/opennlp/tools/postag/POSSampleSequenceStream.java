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

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.Sequence;
import opennlp.tools.ml.model.SequenceStream;
import opennlp.tools.util.ObjectStream;

public class POSSampleSequenceStream implements SequenceStream {

  private POSContextGenerator pcg;
  private ObjectStream<POSSample> psi;

  public POSSampleSequenceStream(ObjectStream<POSSample> psi) throws IOException {
    this(psi, new DefaultPOSContextGenerator(null));
  }

  public POSSampleSequenceStream(ObjectStream<POSSample> psi, POSContextGenerator pcg)
      throws IOException {
    this.psi = psi;
    this.pcg = pcg;
  }

  @SuppressWarnings("unchecked")
  public Event[] updateContext(Sequence sequence, AbstractModel model) {
    Sequence<POSSample> pss = sequence;
    POSTagger tagger = new POSTaggerME(new POSModel("x-unspecified", model, null, new POSTaggerFactory()));
    String[] sentence = pss.getSource().getSentence();
    Object[] ac = pss.getSource().getAddictionalContext();
    String[] tags = tagger.tag(pss.getSource().getSentence());
    Event[] events = new Event[sentence.length];
    POSSampleEventStream.generateEvents(sentence, tags, ac, pcg)
        .toArray(events);
    return events;
  }

  @Override
  public Sequence read() throws IOException {

    POSSample sample = psi.read();

    if (sample != null) {
      String sentence[] = sample.getSentence();
      String tags[] = sample.getTags();
      Event[] events = new Event[sentence.length];

      for (int i = 0; i < sentence.length; i++) {

        // it is safe to pass the tags as previous tags because
        // the context generator does not look for non predicted tags
        String[] context = pcg.getContext(i, sentence, tags, null);

        events[i] = new Event(tags[i], context);
      }
      Sequence<POSSample> sequence = new Sequence<POSSample>(events,sample);
      return sequence;
    }

    return null;
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    psi.reset();
  }

  @Override
  public void close() throws IOException {
    psi.close();
  }
}

