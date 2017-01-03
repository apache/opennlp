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

package opennlp.tools.chunker;

import java.io.IOException;

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.Sequence;
import opennlp.tools.ml.model.SequenceStream;
import opennlp.tools.util.ObjectStream;

public class ChunkSampleSequenceStream implements SequenceStream {

  private final ObjectStream<ChunkSample> samples;
  private final ChunkerContextGenerator contextGenerator;

  public ChunkSampleSequenceStream(ObjectStream<ChunkSample> samples,
      ChunkerContextGenerator contextGenerator) {
    this.samples = samples;
    this.contextGenerator = contextGenerator;
  }

  @Override
  public Sequence read() throws IOException {
    ChunkSample sample = samples.read();

    if (sample != null) {
      String sentence[] = sample.getSentence();
      String tags[] = sample.getTags();
      Event[] events = new Event[sentence.length];

      for (int i = 0; i < sentence.length; i++) {

        // it is safe to pass the tags as previous tags because
        // the context generator does not look for non predicted tags
        String[] context = contextGenerator.getContext(i, sentence, tags, null);

        events[i] = new Event(tags[i], context);
      }
      return new Sequence<>(events,sample);
    }

    return null;
  }

  @Override
  public Event[] updateContext(Sequence sequence, AbstractModel model) {
    // TODO: Should be implemented for Perceptron sequence learning ...
    return null;
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    samples.reset();
  }

  @Override
  public void close() throws IOException {
    samples.close();
  }

}
