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
 
 package opennlp.tools.namefind;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import opennlp.model.AbstractModel;
import opennlp.model.Event;
import opennlp.model.Sequence;
import opennlp.model.SequenceStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;

public class NameSampleSequenceStream implements SequenceStream {

  private NameContextGenerator pcg;
  private List<NameSample> samples;
  
  public NameSampleSequenceStream(ObjectStream<NameSample> psi) throws IOException {
    this(psi, new DefaultNameContextGenerator((AdaptiveFeatureGenerator) null));
  }
  
  public NameSampleSequenceStream(ObjectStream<NameSample> psi, AdaptiveFeatureGenerator featureGen) 
  throws IOException {
    this(psi, new DefaultNameContextGenerator(featureGen));
  }
  
  public NameSampleSequenceStream(ObjectStream<NameSample> psi, NameContextGenerator pcg)
      throws IOException {
    samples = new ArrayList<NameSample>();
    
    NameSample sample;
    while((sample = psi.read()) != null) {
      samples.add(sample);
    }
    
    System.err.println("Got "+samples.size()+" sequences");
    
    this.pcg = pcg;
  }
  
  
  @SuppressWarnings("unchecked")
  public Event[] updateContext(Sequence sequence, AbstractModel model) {
    Sequence<NameSample> pss = sequence;
    TokenNameFinder tagger = new NameFinderME(new TokenNameFinderModel("x-unspecified", model, Collections.<String, Object>emptyMap(), null));
    String[] sentence = pss.getSource().getSentence();
    String[] tags = NameFinderEventStream.generateOutcomes(tagger.find(sentence), null, sentence.length);
    Event[] events = new Event[sentence.length];
    
    NameFinderEventStream.generateEvents(sentence,tags,pcg).toArray(events);
    
    return events;
  }
  
  @SuppressWarnings("unchecked")
  public Iterator<Sequence> iterator() {
    return new NameSampleSequenceIterator(samples.iterator());
  }

}

class NameSampleSequenceIterator implements Iterator<Sequence> {

  private Iterator<NameSample> psi;
  private NameContextGenerator cg;
  
  public NameSampleSequenceIterator(Iterator<NameSample> psi) {
    this.psi = psi;
    cg = new DefaultNameContextGenerator(null);
  }
  
  public boolean hasNext() {
    return psi.hasNext();
  }

  public Sequence<NameSample> next() {
    NameSample sample = psi.next();
    
    String sentence[] = sample.getSentence();
    String tags[] = NameFinderEventStream.generateOutcomes(sample.getNames(), null, sentence.length);
    Event[] events = new Event[sentence.length];
    
    for (int i=0; i < sentence.length; i++) {

      // it is safe to pass the tags as previous tags because
      // the context generator does not look for non predicted tags
      String[] context = cg.getContext(i, sentence, tags, null);

      events[i] = new Event(tags[i], context);
    }
    Sequence<NameSample> sequence = new Sequence<NameSample>(events,sample);
    return sequence;
  }

  public void remove() {
    throw new UnsupportedOperationException();
  }
  
}

