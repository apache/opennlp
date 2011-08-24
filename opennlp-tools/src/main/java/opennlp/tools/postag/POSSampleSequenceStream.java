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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import opennlp.model.AbstractModel;
import opennlp.model.Event;
import opennlp.model.Sequence;
import opennlp.model.SequenceStream;
import opennlp.tools.util.ObjectStream;

public class POSSampleSequenceStream implements SequenceStream {

  private POSContextGenerator pcg;
  private List<POSSample> samples;
  
  public POSSampleSequenceStream(ObjectStream<POSSample> psi) throws IOException {
    this(psi, new DefaultPOSContextGenerator(null));
  }
  
  public POSSampleSequenceStream(ObjectStream<POSSample> psi, POSContextGenerator pcg) 
      throws IOException {
    samples = new ArrayList<POSSample>();
    
    POSSample sample;
    while((sample = psi.read()) != null) {
      samples.add(sample);
    }
    System.err.println("Got "+samples.size()+" sequences");
    this.pcg = pcg;
  }
  
  
  @SuppressWarnings("unchecked")
  public Event[] updateContext(Sequence sequence, AbstractModel model) {
    Sequence<POSSample> pss = sequence;
    POSTagger tagger = new POSTaggerME(new POSModel("x-unspecified", model, null, null));
    String[] sentence = pss.getSource().getSentence();
    String[] tags = tagger.tag(pss.getSource().getSentence());
    Event[] events = new Event[sentence.length];
    POSSampleEventStream.generateEvents(sentence,tags,pcg).toArray(events);
    return events;
  }
  
  @SuppressWarnings("unchecked")
  public Iterator<Sequence> iterator() {
    return new POSSampleSequenceIterator(samples.iterator());
  }

}

class POSSampleSequenceIterator implements Iterator<Sequence> {

  private Iterator<POSSample> psi;
  private POSContextGenerator cg;
  
  public POSSampleSequenceIterator(Iterator<POSSample> psi) {
    this.psi = psi;
    cg = new DefaultPOSContextGenerator(null);
  }
  
  public boolean hasNext() {
    return psi.hasNext();
  }

  public Sequence<POSSample> next() {
    POSSample sample = psi.next();
    
    String sentence[] = sample.getSentence();
    String tags[] = sample.getTags();
    Event[] events = new Event[sentence.length];
    
    for (int i=0; i < sentence.length; i++) {

      // it is safe to pass the tags as previous tags because
      // the context generator does not look for non predicted tags
      String[] context = cg.getContext(i, sentence, tags, null);

      events[i] = new Event(tags[i], context);
    }
    Sequence<POSSample> sequence = new Sequence<POSSample>(events,sample);
    return sequence;
  }

  public void remove() {
    throw new UnsupportedOperationException();
  }
  
}

