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

package opennlp.tools.namefind;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import opennlp.model.Event;
import opennlp.model.EventStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.featuregen.AdditionalContextFeatureGenerator;
import opennlp.tools.util.featuregen.WindowFeatureGenerator;

/**
 * Class for creating an event stream out of data files for training an name
 * finder.
 */
public class NameFinderEventStream extends opennlp.model.AbstractEventStream {

  private ObjectStream<NameSample> nameSampleStream;

  private Iterator<Event> events = Collections.<Event>emptyList().iterator();

  private NameContextGenerator contextGenerator;

  private AdditionalContextFeatureGenerator additionalContextFeatureGenerator = new AdditionalContextFeatureGenerator();

  private String type;
  
  /**
   * Creates a new name finder event stream using the specified data stream and context generator.
   * @param dataStream The data stream of events.
   * @param type null or overrides the type parameter in the provided samples
   * @param contextGenerator The context generator used to generate features for the event stream.
   */
  public NameFinderEventStream(ObjectStream<NameSample> dataStream, String type, NameContextGenerator contextGenerator) {
    this.nameSampleStream = dataStream;
    this.contextGenerator = contextGenerator;
    this.contextGenerator.addFeatureGenerator(new WindowFeatureGenerator(additionalContextFeatureGenerator, 8, 8));
    
    if (type != null)
      this.type = type;
    else
      this.type = "default";
  }

  public NameFinderEventStream(ObjectStream<NameSample> dataStream) {
    this(dataStream, null, new DefaultNameContextGenerator());
  }

  /**
   * Generates the name tag outcomes (start, continue, other) for each token in a sentence
   * with the specified length using the specified name spans.
   * @param names Token spans for each of the names.
   * @param type null or overrides the type parameter in the provided samples
   * @param length The length of the sentence.
   * @return An array of start, continue, other outcomes based on the specified names and sentence length.
   */
  public static String[] generateOutcomes(Span[] names, String type, int length) {
    String[] outcomes = new String[length];
    for (int i = 0; i < outcomes.length; i++) {
      outcomes[i] = NameFinderME.OTHER;
    }
    for (int nameIndex = 0; nameIndex < names.length; nameIndex++) {
      Span name = names[nameIndex];
      if (name.getType() == null) {
        outcomes[name.getStart()] = type + "-" + NameFinderME.START;
      }
      else {
        outcomes[name.getStart()] = name.getType() + "-" + NameFinderME.START;
      }
      // now iterate from begin + 1 till end
      for (int i = name.getStart() + 1; i < name.getEnd(); i++) {
        if (name.getType() == null) {
          outcomes[i] = type + "-" + NameFinderME.CONTINUE;
        }
        else {
          outcomes[i] = name.getType() + "-" + NameFinderME.CONTINUE;
        }
      }
    }
    return outcomes;
  }

  private void createNewEvents() throws IOException {

    // TODO: the iterator of the new events can be empty
    // create as long new events as there are events
    // or the name sample stream is empty
    NameSample sample = null;
    if ((sample = nameSampleStream.read()) != null) {
      if (sample.isClearAdaptiveDataSet()) {
        contextGenerator.clearAdaptiveData();
      }
      //System.err.println(sample);
      String outcomes[] = generateOutcomes(sample.getNames(), type, sample.getSentence().length);
      additionalContextFeatureGenerator.setCurrentContext(sample.getAdditionalContext());
      String[] tokens = new String[sample.getSentence().length];
      List<Event> events = new ArrayList<Event>(outcomes.length);
      for (int i = 0; i < sample.getSentence().length; i++) {
        tokens[i] = sample.getSentence()[i];
      }
      for (int i = 0; i < outcomes.length; i++) {
        events.add(new Event((String) outcomes[i], contextGenerator.getContext(i, sample.getSentence(), outcomes,null)));
      }
      this.events = events.iterator();
      contextGenerator.updateAdaptiveData(tokens, outcomes);
    }
  }

  public boolean hasNext() throws IOException {

    // check if iterator has next event
    if (events.hasNext()) {
      return true;
    } else {
      createNewEvents();

      return events.hasNext();
    }
  }

  public Event next() {
    // call to hasNext() is necessary for reloading elements
    // if the events iterator was already consumed
    if (!events.hasNext()) {
      throw new NoSuchElementException();
    }

    return events.next();
  }

  /**
   * Generated previous decision features for each token based on contents of the specified map.
   * @param tokens The token for which the context is generated.
   * @param prevMap A mapping of tokens to their previous decisions.
   * @return An additional context array with features for each token.
   */
  public static String[][] additionalContext(String[] tokens, Map<String, String> prevMap) {
    String[][] ac = new String[tokens.length][1];
    for (int ti=0;ti<tokens.length;ti++) {
      String pt = (String) prevMap.get(tokens[ti]);
      ac[ti][0]="pd="+pt;
    }
    return ac;

  }

  public static final void main(String[] args) throws java.io.IOException {
    if (args.length != 0) {
      System.err.println("Usage: NameFinderEventStream < training files");
      System.exit(1);
    }
    EventStream es = new NameFinderEventStream(new NameSampleDataStream(new PlainTextByLineStream(new java.io.InputStreamReader(System.in))));
    while (es.hasNext()) {
      System.out.println(es.next());
    }
  }
}
