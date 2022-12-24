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
import java.util.Collections;

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.Sequence;
import opennlp.tools.ml.model.SequenceStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.SequenceCodec;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;

/**
 * A {@link SequenceStream} implementation encapsulating {@link NameSample samples}.
 */
public class NameSampleSequenceStream implements SequenceStream<NameSample> {

  private final NameContextGenerator pcg;
  private final boolean useOutcomes;
  private final ObjectStream<NameSample> psi;
  private final SequenceCodec<String> seqCodec;

  /**
   * Initializes a {@link NameSampleSequenceStream} with given {@code psi} samples.
   *
   * @param psi The data stream of {@link NameSample samples}.
   */
  public NameSampleSequenceStream(ObjectStream<NameSample> psi) {
    this(psi, new DefaultNameContextGenerator((AdaptiveFeatureGenerator) null), true);
  }

  /**
   * Initializes a {@link NameSampleSequenceStream} with given {@code psi} samples
   * and an {@link AdaptiveFeatureGenerator feature generator}.
   *
   * @param psi The data stream of {@link NameSample samples}.
   * @param featureGen The {@link AdaptiveFeatureGenerator feature generator} to use.
   */
  public NameSampleSequenceStream(ObjectStream<NameSample> psi, AdaptiveFeatureGenerator featureGen) {
    this(psi, new DefaultNameContextGenerator(featureGen), true);
  }

  /**
   * Initializes a {@link NameSampleSequenceStream} with given {@code psi} samples
   * and an {@link AdaptiveFeatureGenerator feature generator}.
   *
   * @param psi The data stream of {@link NameSample samples}.
   * @param featureGen The {@link AdaptiveFeatureGenerator feature generator} to use.
   * @param useOutcomes Whether to use outcomes or not.
   */
  public NameSampleSequenceStream(ObjectStream<NameSample> psi,
      AdaptiveFeatureGenerator featureGen, boolean useOutcomes) {
    this(psi, new DefaultNameContextGenerator(featureGen), useOutcomes);
  }

  /**
   * Initializes a {@link NameSampleSequenceStream} with given {@code psi} samples
   * and an {@link AdaptiveFeatureGenerator feature generator}.
   *
   * @param psi The data stream of {@link NameSample samples}.
   * @param pcg The {@link NameContextGenerator context generator} to use.
   */
  public NameSampleSequenceStream(ObjectStream<NameSample> psi, NameContextGenerator pcg) {
    this(psi, pcg, true);
  }

  /**
   * Initializes a {@link NameSampleSequenceStream} with given {@code psi} samples
   * and an {@link AdaptiveFeatureGenerator feature generator}.
   *
   * @param psi The data stream of {@link NameSample samples}.
   * @param pcg The {@link NameContextGenerator context generator} to use.
   * @param useOutcomes Whether to use outcomes or not.
   */
  public NameSampleSequenceStream(ObjectStream<NameSample> psi, NameContextGenerator pcg,
                                  boolean useOutcomes) {
    this(psi, pcg, useOutcomes, new BioCodec());
  }

  /**
   * Initializes a {@link NameSampleSequenceStream} with given {@code psi} samples
   * and an {@link AdaptiveFeatureGenerator feature generator}.
   *
   * @param psi The data stream of {@link NameSample samples}.
   * @param pcg The {@link NameContextGenerator context generator} to use.
   * @param useOutcomes Whether to use outcomes or not.
   * @param seqCodec The {@link SequenceCodec} to use.
   */
  public NameSampleSequenceStream(ObjectStream<NameSample> psi, NameContextGenerator pcg, boolean useOutcomes,
      SequenceCodec<String> seqCodec) {
    this.psi = psi;
    this.useOutcomes = useOutcomes;
    this.pcg = pcg;
    this.seqCodec = seqCodec;
  }

  @Override
  public Event[] updateContext(Sequence<NameSample> sequence, AbstractModel model) {
    TokenNameFinder tagger = new NameFinderME(new TokenNameFinderModel(
        "x-unspecified", model, Collections.emptyMap(), null));
    String[] sentence = sequence.getSource().getSentence();
    String[] tags = seqCodec.encode(tagger.find(sentence), sentence.length);
    Event[] events = new Event[sentence.length];

    NameFinderEventStream.generateEvents(sentence,tags,pcg).toArray(events);

    return events;
  }

  @Override
  public Sequence<NameSample> read() throws IOException {
    NameSample sample = psi.read();
    if (sample != null) {
      String[] sentence = sample.getSentence();
      String[] tags = seqCodec.encode(sample.getNames(), sentence.length);
      Event[] events = new Event[sentence.length];

      for (int i = 0; i < sentence.length; i++) {

        // it is safe to pass the tags as previous tags because
        // the context generator does not look for non predicted tags
        String[] context;
        if (useOutcomes) {
          context = pcg.getContext(i, sentence, tags, null);
        }
        else {
          context = pcg.getContext(i, sentence, null, null);
        }

        events[i] = new Event(tags[i], context);
      }
      return new Sequence<>(events,sample);
    }
    else {
      return null;
    }
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

