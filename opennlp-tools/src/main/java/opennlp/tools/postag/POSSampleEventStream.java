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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import opennlp.tools.ml.model.Event;
import opennlp.tools.util.AbstractEventStream;
import opennlp.tools.util.ObjectStream;

/**
 * This class reads the {@link POSSample}s from the given {@link Iterator}
 * and converts the {@link POSSample}s into {@link Event}s which
 * can be used by the maxent library for training.
 */
public class POSSampleEventStream extends AbstractEventStream<POSSample> {

  /**
   * The {@link POSContextGenerator} used
   * to create the training {@link Event}s.
   */
  private POSContextGenerator cg;

  /**
   * Initializes the current instance with the given samples and the
   * given {@link POSContextGenerator}.
   *
   * @param samples
   * @param cg
   */
  public POSSampleEventStream(ObjectStream<POSSample> samples, POSContextGenerator cg) {
    super(samples);

    this.cg = cg;
  }

  /**
   * Initializes the current instance with given samples
   * and a {@link DefaultPOSContextGenerator}.
   * @param samples
   */
  public POSSampleEventStream(ObjectStream<POSSample> samples) {
    this(samples, new DefaultPOSContextGenerator(null));
  }

  @Override
  protected Iterator<Event> createEvents(POSSample sample) {
    String sentence[] = sample.getSentence();
    String tags[] = sample.getTags();
    Object ac[] = sample.getAddictionalContext();
    List<Event> events = generateEvents(sentence, tags, ac, cg);
    return events.iterator();
  }

  public static List<Event> generateEvents(String[] sentence, String[] tags,
      Object[] additionalContext, POSContextGenerator cg) {
    List<Event> events = new ArrayList<Event>(sentence.length);

    for (int i = 0; i < sentence.length; i++) {

      // it is safe to pass the tags as previous tags because
      // the context generator does not look for non predicted tags
      String[] context = cg.getContext(i, sentence, tags, additionalContext);

      events.add(new Event(tags[i], context));
    }
    return events;
  }

  public static List<Event> generateEvents(String[] sentence, String[] tags,
      POSContextGenerator cg) {
    return generateEvents(sentence, tags, null, cg);
  }
}
