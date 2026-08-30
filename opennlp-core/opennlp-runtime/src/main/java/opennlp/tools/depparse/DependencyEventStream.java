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

package opennlp.tools.depparse;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.ml.model.Event;
import opennlp.tools.util.ObjectStream;

/**
 * Turns {@link DependencySample samples} into training {@link Event events}: for each
 * sample the {@link ArcStandardOracle} derives the gold transitions, and every transition
 * becomes one event pairing the configuration features with the encoded transition.
 *
 * <p>Samples whose graph has no arc-standard derivation, that is non-projective trees,
 * are skipped and counted; the count is logged once the stream is exhausted.</p>
 */
class DependencyEventStream implements ObjectStream<Event> {

  private static final Logger logger = LoggerFactory.getLogger(DependencyEventStream.class);

  private final ObjectStream<DependencySample> samples;
  private final DependencyContextGenerator contextGenerator;
  private final Deque<Event> pending = new ArrayDeque<>();

  private int skipped;

  /**
   * Initializes the stream.
   *
   * @param samples The samples to convert. Must not be {@code null}.
   * @param contextGenerator The feature generator. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if any parameter is {@code null}.
   */
  DependencyEventStream(ObjectStream<DependencySample> samples,
      DependencyContextGenerator contextGenerator) {
    if (samples == null || contextGenerator == null) {
      throw new IllegalArgumentException("samples and contextGenerator must not be null");
    }
    this.samples = samples;
    this.contextGenerator = contextGenerator;
  }

  @Override
  public Event read() throws IOException {
    while (pending.isEmpty()) {
      final DependencySample sample = samples.read();
      if (sample == null) {
        if (skipped > 0) {
          logger.warn("Skipped {} non-projective sample(s) without an arc-standard derivation.",
              skipped);
          skipped = 0;
        }
        return null;
      }
      final List<Transition> transitions;
      try {
        transitions = ArcStandardOracle.transitions(sample.getGraph());
      } catch (IllegalArgumentException e) {
        skipped++;
        continue;
      }
      final ArcStandardState state = new ArcStandardState(sample.getGraph().size());
      final String[] tokens = sample.getTokens();
      final String[] tags = sample.getTags();
      for (final Transition transition : transitions) {
        pending.add(new Event(transition.encode(),
            contextGenerator.getContext(state, tokens, tags)));
        state.apply(transition);
      }
    }
    return pending.poll();
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    samples.reset();
    pending.clear();
    skipped = 0;
  }

  @Override
  public void close() throws IOException {
    samples.close();
  }
}
