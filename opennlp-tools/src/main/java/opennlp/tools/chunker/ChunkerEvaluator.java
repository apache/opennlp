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

import opennlp.tools.util.eval.Evaluator;
import opennlp.tools.util.eval.FMeasure;

/**
 * The {@link ChunkerEvaluator} measures the performance
 * of the given {@link Chunker} with the provided
 * reference {@link ChunkSample}s.
 *
 * @see Evaluator
 * @see Chunker
 * @see ChunkSample
 */
public class ChunkerEvaluator extends Evaluator<ChunkSample> {

  private FMeasure fmeasure = new FMeasure();

  /**
   * The {@link Chunker} used to create the predicted
   * {@link ChunkSample} objects.
   */
  private Chunker chunker;

  /**
   * Initializes the current instance with the given
   * {@link Chunker}.
   *
   * @param chunker the {@link Chunker} to evaluate.
   * @param listeners evaluation listeners
   */
  public ChunkerEvaluator(Chunker chunker, ChunkerEvaluationMonitor... listeners) {
    super(listeners);
    this.chunker = chunker;
  }

  /**
   * Evaluates the given reference {@link ChunkSample} object.
   *
   * This is done by finding the phrases with the
   * {@link Chunker} in the sentence from the reference
   * {@link ChunkSample}. The found phrases are then used to
   * calculate and update the scores.
   *
   * @param reference the reference {@link ChunkSample}.
   *
   * @return the predicted sample
   */
  @Override
  protected ChunkSample processSample(ChunkSample reference) {
    String[] preds = chunker.chunk(reference.getSentence(), reference.getTags());
    ChunkSample result = new ChunkSample(reference.getSentence(), reference.getTags(), preds);

    fmeasure.updateScores(reference.getPhrasesAsSpanList(), result.getPhrasesAsSpanList());

    return result;
  }

  public FMeasure getFMeasure() {
    return fmeasure;
  }

}
