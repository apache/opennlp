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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import opennlp.tools.ml.model.Event;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.SequenceCodec;
import opennlp.tools.util.Span;
import opennlp.tools.util.featuregen.AdditionalContextFeatureGenerator;
import opennlp.tools.util.featuregen.WindowFeatureGenerator;

/**
 * Class for creating an event stream out of data files for training an name
 * finder.
 */
public class NameFinderEventStream extends opennlp.tools.util.AbstractEventStream<NameSample> {

  private NameContextGenerator contextGenerator;

  private AdditionalContextFeatureGenerator additionalContextFeatureGenerator =
      new AdditionalContextFeatureGenerator();

  private SequenceCodec<String> codec;

  private final String defaultType;

  /**
   * Creates a new name finder event stream using the specified data stream and context generator.
   * @param dataStream The data stream of events.
   * @param type null or overrides the type parameter in the provided samples
   * @param contextGenerator The context generator used to generate features for the event stream.
   */
  public NameFinderEventStream(ObjectStream<NameSample> dataStream, String type,
                               NameContextGenerator contextGenerator, SequenceCodec<String> codec) {
    super(dataStream);

    this.codec = codec;

    if (codec == null) {
      this.codec = new BioCodec();
    }

    this.contextGenerator = contextGenerator;
    this.contextGenerator.addFeatureGenerator(
        new WindowFeatureGenerator(additionalContextFeatureGenerator, 8, 8));

    this.defaultType = type;
  }

  public NameFinderEventStream(ObjectStream<NameSample> dataStream) {
    this(dataStream, null, new DefaultNameContextGenerator(), null);
  }

  /**
   * Generates the name tag outcomes (start, continue, other) for each token in a sentence
   * with the specified length using the specified name spans.
   * @param names Token spans for each of the names.
   * @param type null or overrides the type parameter in the provided samples
   * @param length The length of the sentence.
   * @return An array of start, continue, other outcomes based on the specified names and sentence length.
   *
   * @deprecated use the BioCodec implementation of the SequenceValidator instead!
   */
  @Deprecated
  public static String[] generateOutcomes(Span[] names, String type, int length) {
    String[] outcomes = new String[length];
    for (int i = 0; i < outcomes.length; i++) {
      outcomes[i] = NameFinderME.OTHER;
    }
    for (Span name : names) {
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

  public static List<Event> generateEvents(String[] sentence, String[] outcomes,
                                           NameContextGenerator cg) {
    List<Event> events = new ArrayList<>(outcomes.length);
    for (int i = 0; i < outcomes.length; i++) {
      events.add(new Event(outcomes[i], cg.getContext(i, sentence, outcomes,null)));
    }

    cg.updateAdaptiveData(sentence, outcomes);

    return events;
  }

  @Override
  protected Iterator<Event> createEvents(NameSample sample) {

    if (sample.isClearAdaptiveDataSet()) {
      contextGenerator.clearAdaptiveData();
    }

    Span[] names = sample.getNames();
    if (!Objects.isNull(this.defaultType)) {
      overrideType(names);
    }

    String outcomes[] = codec.encode(names, sample.getSentence().length);
    // String outcomes[] = generateOutcomes(sample.getNames(), type, sample.getSentence().length);
    additionalContextFeatureGenerator.setCurrentContext(sample.getAdditionalContext());
    String[] tokens = new String[sample.getSentence().length];

    for (int i = 0; i < sample.getSentence().length; i++) {
      tokens[i] = sample.getSentence()[i];
    }

    return generateEvents(tokens, outcomes, contextGenerator).iterator();
  }

  private void overrideType(Span[] names) {
    for (int i = 0; i < names.length; i++) {
      Span n = names[i];
      names[i] = new Span(n.getStart(), n.getEnd(), this.defaultType,
              n.getProb());
    }
  }

  /**
   * Generated previous decision features for each token based on contents of the specified map.
   * @param tokens The token for which the context is generated.
   * @param prevMap A mapping of tokens to their previous decisions.
   * @return An additional context array with features for each token.
   */
  public static String[][] additionalContext(String[] tokens, Map<String, String> prevMap) {
    String[][] ac = new String[tokens.length][1];
    for (int ti = 0; ti < tokens.length; ti++) {
      String pt = prevMap.get(tokens[ti]);
      ac[ti][0] = "pd=" + pt;
    }
    return ac;
  }
}
